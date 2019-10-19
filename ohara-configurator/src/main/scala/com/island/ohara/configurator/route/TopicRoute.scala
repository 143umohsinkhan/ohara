/*
 * Copyright 2019 is-land
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.island.ohara.configurator.route
import akka.http.scaladsl.server
import com.island.ohara.agent.{BrokerCollie, NoSuchClusterException}
import com.island.ohara.client.configurator.v0.BrokerApi.BrokerClusterInfo
import com.island.ohara.client.configurator.v0.MetricsApi.Metrics
import com.island.ohara.client.configurator.v0.TopicApi._
import com.island.ohara.client.kafka.TopicAdmin
import com.island.ohara.common.setting.{ObjectKey, TopicKey}
import com.island.ohara.common.util.{CommonUtils, Releasable}
import com.island.ohara.configurator.route.hook._
import com.island.ohara.configurator.store.{DataStore, MeterCache}
import com.typesafe.scalalogging.Logger
import spray.json.JsString

import scala.concurrent.{ExecutionContext, Future}
private[configurator] object TopicRoute {
  private[this] val LOG = Logger(TopicRoute.getClass)

  /**
    * convert the setting defs to plain map.
    */
  private[route] val TOPIC_CUSTOM_CONFIGS: Map[String, JsString] =
    TOPIC_CUSTOM_DEFINITIONS.map(setting => setting.key() -> JsString(setting.defaultValue())).toMap

  /**
    * fetch the topic meters from broker cluster
    * @param brokerCluster the broker cluster hosting the topic
    * @param topicName topic name which used to filter the correct meter
    * @return meters belong to the input topic
    */
  private[this] def metrics(brokerCluster: BrokerClusterInfo, topicName: String)(
    implicit meterCache: MeterCache): Metrics = Metrics(
    meterCache.meters(brokerCluster).getOrElse(topicName, Seq.empty))

  /**
    * update the metrics for input topic
    * @param topicInfo topic info
    * @return updated topic info
    */
  private[this] def updateState(topicInfo: TopicInfo)(implicit meterCache: MeterCache,
                                                      store: DataStore,
                                                      brokerCollie: BrokerCollie,
                                                      executionContext: ExecutionContext): Future[TopicInfo] = store
    .value[BrokerClusterInfo](topicInfo.brokerClusterKey)
    .flatMap(c => brokerCollie.topicAdmin(c).map(_ -> c))
    .flatMap {
      case (topicAdmin, brokerClusterInfo) =>
        topicAdmin
          .exist(topicInfo.key)
          .flatMap(
            if (_)
              topicAdmin
                .topics()
                .map(_.find(_.name == topicInfo.key.topicNameOnKafka()).get)
                .map(_.partitionInfos -> Some(TopicState.RUNNING))
            else Future.successful(Seq.empty -> None))
          // pre-close topic admin
          .map(v =>
            try v
            finally Releasable.close(topicAdmin))
          .map {
            case (partitions, state) =>
              topicInfo.copy(
                partitionInfos = partitions.map(partition =>
                  PartitionInfo(
                    index = partition.index,
                    leaderNode = partition.leaderNode,
                    replicaNodes = partition.replicaNodes,
                    inSyncReplicaNodes = partition.inSyncReplicaNodes,
                    beginningOffset = partition.beginningOffset,
                    endOffset = partition.endOffset
                )),
                state = state,
                metrics = metrics(brokerClusterInfo, topicInfo.key.topicNameOnKafka)
              )
          }
    }
    .recover {
      case e: Throwable =>
        LOG.debug(s"failed to fetch stats for $topicInfo", e)
        topicInfo.copy(
          partitionInfos = Seq.empty,
          metrics = Metrics.EMPTY,
          state = None
        )
    }

  private[this] def createTopic(topicAdmin: TopicAdmin, topicInfo: TopicInfo)(
    implicit executionContext: ExecutionContext): Future[TopicInfo] =
    topicAdmin.creator
      .topicKey(topicInfo.key)
      .numberOfPartitions(topicInfo.numberOfPartitions)
      .numberOfReplications(topicInfo.numberOfReplications)
      .configs(topicInfo.configs)
      .create()
      .map { _ =>
        try topicInfo.copy(
          // the topic is just created so we don't fetch the "empty" metrics actually.
          metrics = Metrics.EMPTY,
          state = Some(TopicState.RUNNING),
          lastModified = CommonUtils.current(),
        )
        finally topicAdmin.close()
      }

  private[this] def hookOfGet(implicit meterCache: MeterCache,
                              brokerCollie: BrokerCollie,
                              store: DataStore,
                              executionContext: ExecutionContext): HookOfGet[TopicInfo] = (topicInfo: TopicInfo) =>
    updateState(topicInfo)

  private[this] def hookOfList(implicit meterCache: MeterCache,
                               brokerCollie: BrokerCollie,
                               store: DataStore,
                               executionContext: ExecutionContext): HookOfList[TopicInfo] =
    (topicInfos: Seq[TopicInfo]) => Future.traverse(topicInfos)(updateState)

  private[this] def creationToTopicInfo(creation: Creation)(implicit store: DataStore,
                                                            executionContext: ExecutionContext): Future[TopicInfo] =
    store.exist[BrokerClusterInfo](creation.brokerClusterKey).map {
      if (_)
        TopicInfo(
          settings = TOPIC_CUSTOM_CONFIGS ++ creation.settings,
          partitionInfos = Seq.empty,
          metrics = Metrics.EMPTY,
          state = None,
          lastModified = CommonUtils.current()
        )
      else throw new IllegalArgumentException(s"broker cluster:${creation.brokerClusterKey} does not exist")
    }

  private[this] def hookOfCreation(implicit store: DataStore,
                                   executionContext: ExecutionContext): HookOfCreation[Creation, TopicInfo] =
    creationToTopicInfo(_)

  private[this] def hookOfUpdating(implicit adminCleaner: AdminCleaner,
                                   meterCache: MeterCache,
                                   brokerCollie: BrokerCollie,
                                   store: DataStore,
                                   executionContext: ExecutionContext): HookOfUpdating[Updating, TopicInfo] =
    (key: ObjectKey, update: Updating, previousOption: Option[TopicInfo]) =>
      if (previousOption.isEmpty) creationToTopicInfo(access.request.settings(update.settings).creation)
      else
        // we have to check whether topic is running on previous cluster
        CollieUtils.topicAdmin(previousOption.get.brokerClusterKey).flatMap {
          case (_, client) =>
            client.topics().map(_.exists(_.name == TopicKey.of(key.group, key.name).topicNameOnKafka)).flatMap {
              try if (_)
                throw new IllegalStateException(
                  s"the topic:$key is working now. Please stop it before updating the properties")
              else {

                // 1) fill the previous settings (if exists)
                // 2) overwrite previous settings by updated settings
                // 3) fill the ignored settings by creation
                creationToTopicInfo(
                  access.request
                    .settings(previousOption.map(_.settings).getOrElse(Map.empty))
                    .settings(update.settings)
                    // the key is not in update's settings so we have to add it to settings
                    .name(key.name)
                    .group(key.group)
                    .creation)
              } finally Releasable.close(client)
            }
      }

  private[this] def hookBeforeDelete(implicit store: DataStore,
                                     meterCache: MeterCache,
                                     adminCleaner: AdminCleaner,
                                     brokerCollie: BrokerCollie,
                                     executionContext: ExecutionContext): HookBeforeDelete = (key: ObjectKey) =>
    store
      .get[TopicInfo](key)
      .flatMap(_.fold(Future.unit) { topicInfo =>
        CollieUtils
          .topicAdmin(topicInfo.brokerClusterKey)
          .flatMap {
            case (_, client) =>
              client.exist(topicInfo.key).flatMap {
                if (_)
                  try Future.failed(
                    new IllegalStateException(s"the topic:${topicInfo.key} is running. Please stop it first"))
                  finally Releasable.close(client)
                else
                  try Future.unit
                  finally Releasable.close(client)
              }
          }
          .recover {
            case e: NoSuchClusterException =>
              LOG.warn(
                s"the cluster:${topicInfo.brokerClusterKey} doesn't exist!!! just remove topic from configurator",
                e)
          }
      })

  private[this] def hookOfStart(implicit store: DataStore,
                                meterCache: MeterCache,
                                adminCleaner: AdminCleaner,
                                brokerCollie: BrokerCollie,
                                executionContext: ExecutionContext): HookOfAction =
    (key: ObjectKey, _, _) =>
      store
        .value[TopicInfo](key)
        .flatMap(topicInfo =>
          CollieUtils.topicAdmin(topicInfo.brokerClusterKey).flatMap {
            case (_, client) =>
              client.exist(topicInfo.key).flatMap {
                if (_) Future.unit
                else
                  createTopic(
                    topicAdmin = client,
                    topicInfo = topicInfo
                  ).map(_ => Unit)
              }
        })

  private[this] def hookOfStop(implicit store: DataStore,
                               meterCache: MeterCache,
                               adminCleaner: AdminCleaner,
                               brokerCollie: BrokerCollie,
                               executionContext: ExecutionContext): HookOfAction =
    (key: ObjectKey, _, _) =>
      store.value[TopicInfo](key).flatMap { topicInfo =>
        CollieUtils
          .topicAdmin(topicInfo.brokerClusterKey)
          .flatMap {
            case (_, client) =>
              client.exist(topicInfo.key).flatMap {
                if (_) client.delete(topicInfo.key).flatMap(_ => Future.unit)
                else Future.unit
              }
          }
          .recover {
            case e: Throwable =>
              LOG.error(s"failed to remove topic:${topicInfo.name} from kafka", e)
          }
    }

  def apply(implicit store: DataStore,
            adminCleaner: AdminCleaner,
            meterCache: MeterCache,
            brokerCollie: BrokerCollie,
            executionContext: ExecutionContext): server.Route =
    RouteBuilder[Creation, Updating, TopicInfo]()
      .root(TOPICS_PREFIX_PATH)
      .hookOfCreation(hookOfCreation)
      .hookOfUpdating(hookOfUpdating)
      .hookOfGet(hookOfGet)
      .hookOfList(hookOfList)
      .hookBeforeDelete(hookBeforeDelete)
      .hookOfPutAction(START_COMMAND, hookOfStart)
      .hookOfPutAction(STOP_COMMAND, hookOfStop)
      .build()
}
