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

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server
import com.island.ohara.agent.{BrokerCollie, NoSuchClusterException, WorkerCollie}
import com.island.ohara.client.configurator.v0.ConnectorApi._
import com.island.ohara.client.configurator.v0.MetricsApi.Metrics
import com.island.ohara.client.configurator.v0.WorkerApi.WorkerClusterInfo
import com.island.ohara.client.kafka.WorkerClient
import com.island.ohara.common.setting.{ConnectorKey, ObjectKey}
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.configurator.route.hook._
import com.island.ohara.configurator.store.{DataStore, MeterCache}
import com.typesafe.scalalogging.Logger

import scala.concurrent.{ExecutionContext, Future}
private[configurator] object ConnectorRoute extends SprayJsonSupport {

  private[this] lazy val LOG = Logger(ConnectorRoute.getClass)

  private[this] def toRes(request: Creation) =
    ConnectorDescription(
      settings = request.settings,
      // we don't need to fetch connector from kafka since it has not existed in kafka.
      status = None,
      tasksStatus = Seq.empty,
      metrics = Metrics.EMPTY,
      lastModified = CommonUtils.current()
    )

  private[this] def updateState(connectorConfig: ConnectorDescription,
                                cluster: WorkerClusterInfo,
                                workerClient: WorkerClient)(implicit executionContext: ExecutionContext,
                                                            meterCache: MeterCache): Future[ConnectorDescription] =
    workerClient
      .statusOrNone(connectorConfig.key)
      .recover {
        case e: Throwable =>
          val message = s"failed to fetch stats for $connectorConfig"
          LOG.error(message, e)
          None
      }
      .map(_.map { connectorInfoFromKafka =>
        connectorConfig.copy(
          status = Some(Status(
            state = State.forName(connectorInfoFromKafka.connector.state),
            error = connectorInfoFromKafka.connector.trace,
            nodeName = connectorInfoFromKafka.connector.workerHostname
          )),
          tasksStatus = connectorInfoFromKafka.tasks.map { taskStatus =>
            Status(
              state = State.forName(taskStatus.state),
              error = taskStatus.trace,
              nodeName = taskStatus.workerHostname
            )
          },
          metrics = Metrics(meterCache.meters(cluster).getOrElse(connectorConfig.key.connectorNameOnKafka, Seq.empty))
        )
      }.getOrElse(connectorConfig))

  private[this] def hookOfGet(implicit workerCollie: WorkerCollie,
                              executionContext: ExecutionContext,
                              meterCache: MeterCache): HookOfGet[ConnectorDescription] =
    (connectorDescription: ConnectorDescription) =>
      CollieUtils.workerClient(connectorDescription.workerClusterName).flatMap {
        case (cluster, wkClient) => updateState(connectorDescription, cluster, wkClient)
    }

  private[this] def hookOfList(implicit workerCollie: WorkerCollie,
                               executionContext: ExecutionContext,
                               meterCache: MeterCache): HookOfList[ConnectorDescription] =
    (connectorDescriptions: Seq[ConnectorDescription]) =>
      Future.sequence(connectorDescriptions.map { connectorDescription =>
        CollieUtils.workerClient(connectorDescription.workerClusterName).flatMap {
          case (cluster, wkClient) => updateState(connectorDescription, cluster, wkClient)
        }
      })

  private[this] def hookOfCreation(implicit workerCollie: WorkerCollie,
                                   executionContext: ExecutionContext): HookOfCreation[Creation, ConnectorDescription] =
    (creation: Creation) =>
      creation.workerClusterName.map(Future.successful).getOrElse(CollieUtils.singleCluster()).map { clusterName =>
        toRes(new Creation(access.request.settings(creation.settings).workerClusterName(clusterName).creation.settings))
    }

  private[this] def HookOfUpdating(implicit workerCollie: WorkerCollie,
                                   executionContext: ExecutionContext,
                                   meterCache: MeterCache): HookOfUpdating[Creation, Updating, ConnectorDescription] =
    (key: ObjectKey, update: Updating, previous: Option[ConnectorDescription]) =>
      // 1) find the connector (the connector may be nonexistent)
      previous
        .map { desc =>
          CollieUtils.workerClient(desc.workerClusterName).flatMap {
            case (cluster, client) =>
              updateState(desc, cluster, client).map(_.status.map(_.state))
          }
        }
        .getOrElse(Future.successful(None))
        .flatMap { previousState =>
          // 2) throw exception if previous connector exist and is working
          if (previousState.isDefined)
            throw new IllegalStateException(
              "the connector is working now. Please stop it before updating the properties")
          // 3) locate the correct worker cluster name
          update.workerClusterName
            .orElse(previous.map(_.workerClusterName))
            .map(Future.successful)
            .getOrElse(CollieUtils.singleCluster())
        }
        .map { clusterName =>
          toRes(
            access.request
              .settings(previous.map(_.settings).getOrElse(Map.empty))
              .settings(update.settings)
              // rewrite the group and name to prevent user updates the both group and name.
              .name(key.name)
              .group(key.group)
              .workerClusterName(clusterName)
              .creation)
      }

  private[this] def hookBeforeDelete(implicit store: DataStore,
                                     workerCollie: WorkerCollie,
                                     executionContext: ExecutionContext): HookBeforeDelete = (key: ObjectKey) =>
    store
      .get[ConnectorDescription](key)
      .flatMap(_.map { connectorDescription =>
        CollieUtils
          .workerClient(connectorDescription.workerClusterName)
          .flatMap {
            case (_, wkClient) =>
              wkClient.exist(connectorDescription.key).flatMap {
                if (_)
                  throw new IllegalStateException(
                    "the connector is working now. Please stop it before deleting the properties")
                else Future.unit
              }
          }
          .recoverWith {
            // Connector can't live without cluster...
            case _: NoSuchClusterException => Future.unit
          }
      }.getOrElse(Future.unit))

  private[this] def hookOfStart(implicit store: DataStore,
                                adminCleaner: AdminCleaner,
                                brokerCollie: BrokerCollie,
                                workerCollie: WorkerCollie,
                                executionContext: ExecutionContext): HookOfAction =
    (key: ObjectKey, _, _) =>
      store.value[ConnectorDescription](key).flatMap { connectorDesc =>
        CollieUtils
          .both(connectorDesc.workerClusterName)
          .flatMap {
            case (_, topicAdmin, cluster, wkClient) =>
              topicAdmin.topics().map(topics => (cluster, wkClient, topics))
          }
          .flatMap {
            case (_, wkClient, topicInfos) =>
              connectorDesc.topicKeys.foreach { key =>
                if (!topicInfos.exists(_.name == key.topicNameOnKafka()))
                  throw new NoSuchElementException(s"topic:$key is not running")
              }
              if (connectorDesc.topicKeys.isEmpty) throw new IllegalArgumentException("topics are required")
              wkClient.exist(connectorDesc.key).flatMap {
                if (_) Future.unit
                else
                  wkClient
                    .connectorCreator()
                    .settings(connectorDesc.plain)
                    // always override the name
                    .connectorKey(connectorDesc.key)
                    .threadPool(executionContext)
                    .topicKeys(connectorDesc.topicKeys)
                    .create()
                    .map(_ => Unit)
              }
          }
    }

  private[this] def hookOfStop(implicit store: DataStore,
                               workerCollie: WorkerCollie,
                               executionContext: ExecutionContext): HookOfAction =
    (key: ObjectKey, _, _) =>
      store.value[ConnectorDescription](key).flatMap { connectorConfig =>
        CollieUtils.workerClient(connectorConfig.workerClusterName).flatMap {
          case (_, wkClient) =>
            wkClient.exist(connectorConfig.key).flatMap {
              if (_) wkClient.delete(connectorConfig.key).map(_ => Unit)
              else Future.unit
            }
        }
    }

  private[this] def hookOfPause(implicit store: DataStore,
                                workerCollie: WorkerCollie,
                                executionContext: ExecutionContext): HookOfAction =
    (key: ObjectKey, _, _) =>
      store.value[ConnectorDescription](key).flatMap { connectorConfig =>
        CollieUtils.workerClient(connectorConfig.workerClusterName).flatMap {
          case (_, wkClient) =>
            wkClient.status(ConnectorKey.of(key.group, key.name)).map(_.connector.state).flatMap {
              case State.PAUSED.name => Future.unit
              case _ =>
                wkClient.pause(ConnectorKey.of(key.group, key.name)).map(_ => Unit)
            }
        }
    }

  private[this] def hookOfResume(implicit store: DataStore,
                                 workerCollie: WorkerCollie,
                                 executionContext: ExecutionContext): HookOfAction =
    (key: ObjectKey, _, _) =>
      store.value[ConnectorDescription](key).flatMap { connectorConfig =>
        CollieUtils.workerClient(connectorConfig.workerClusterName).flatMap {
          case (_, wkClient) =>
            wkClient.status(ConnectorKey.of(key.group, key.name)).map(_.connector.state).flatMap {
              case State.PAUSED.name =>
                wkClient.resume(ConnectorKey.of(key.group, key.name)).map(_ => Unit)
              case _ => Future.unit
            }
        }
    }

  def apply(implicit store: DataStore,
            adminCleaner: AdminCleaner,
            brokerCollie: BrokerCollie,
            workerCollie: WorkerCollie,
            executionContext: ExecutionContext,
            meterCache: MeterCache): server.Route =
    route[Creation, Updating, ConnectorDescription](
      root = CONNECTORS_PREFIX_PATH,
      hookOfCreation = hookOfCreation,
      HookOfUpdating = HookOfUpdating,
      hookOfGet = hookOfGet,
      hookOfList = hookOfList,
      hookBeforeDelete = hookBeforeDelete,
      hookOfStart = hookOfStart,
      hookOfStop = hookOfStop,
      hookOfPause = hookOfPause,
      hookOfResume = hookOfResume
    )
}
