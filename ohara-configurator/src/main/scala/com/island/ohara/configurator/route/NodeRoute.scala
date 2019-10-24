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
import com.island.ohara.agent.ServiceCollie
import com.island.ohara.client.configurator.v0.ClusterInfo
import com.island.ohara.client.configurator.v0.NodeApi._
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.configurator.route.ObjectChecker.ObjectCheckException
import com.island.ohara.configurator.route.hook._
import com.island.ohara.configurator.store.DataStore
import com.typesafe.scalalogging.Logger

import scala.concurrent.{ExecutionContext, Future}
object NodeRoute {
  private[this] lazy val LOG = Logger(NodeRoute.getClass)

  private[this] def updateServices(node: Node)(implicit serviceCollie: ServiceCollie,
                                               executionContext: ExecutionContext): Future[Node] =
    updateServices(Seq(node)).map(_.head)

  private[this] def updateServices(nodes: Seq[Node])(implicit serviceCollie: ServiceCollie,
                                                     executionContext: ExecutionContext): Future[Seq[Node]] =
    serviceCollie.fetchServices(nodes).recover {
      case e: Throwable =>
        LOG.error("failed to seek cluster information", e)
        nodes
    }

  private[this] def hookOfGet(implicit serviceCollie: ServiceCollie,
                              executionContext: ExecutionContext): HookOfGet[Node] = updateServices(_)

  private[this] def hookOfList(implicit serviceCollie: ServiceCollie,
                               executionContext: ExecutionContext): HookOfList[Node] =
    Future.traverse(_)(updateServices)

  private[this] def creationToNode(creation: Creation): Future[Node] = Future.successful(
    Node(
      hostname = creation.hostname,
      port = creation.port,
      user = creation.user,
      password = creation.password,
      services = Seq.empty,
      lastModified = CommonUtils.current(),
      validationReport = None,
      tags = creation.tags
    ))

  private[this] def hookOfCreation: HookOfCreation[Creation, Node] = creationToNode(_)

  private[this] def checkConflict(nodeName: String, serviceName: String, clusterInfos: Seq[ClusterInfo]): Unit = {
    val conflicted = clusterInfos.filter(_.nodeNames.contains(nodeName))
    if (conflicted.nonEmpty)
      throw new IllegalArgumentException(s"node:$nodeName is used by $serviceName.")
  }

  private[this] def hookOfUpdating(implicit objectChecker: ObjectChecker,
                                   executionContext: ExecutionContext): HookOfUpdating[Updating, Node] =
    (key: ObjectKey, updating: Updating, previousOption: Option[Node]) =>
      previousOption match {
        case None =>
          creationToNode(
            Creation(
              hostname = key.name(),
              port = updating.port,
              user = updating.user,
              password = updating.password,
              tags = updating.tags.getOrElse(Map.empty)
            ))
        case Some(previous) =>
          objectChecker.checkList
            .allZookeepers()
            .allBrokers()
            .allWorkers()
            .allStreamApps()
            .check()
            .map(report =>
              (report.runningZookeepers, report.runningBrokers, report.runningWorkers, report.runningStreamApps))
            .flatMap {
              case (zookeeperClusterInfos, brokerClusterInfos, workerClusterInfos, streamClusterInfos) =>
                checkConflict(key.name, "zookeeper cluster", zookeeperClusterInfos)
                checkConflict(key.name, "broker cluster", brokerClusterInfos)
                checkConflict(key.name, "worker cluster", workerClusterInfos)
                checkConflict(key.name, "stream cluster", streamClusterInfos)
                creationToNode(
                  Creation(
                    hostname = key.name(),
                    port = updating.port.orElse(previous.port),
                    user = updating.user.orElse(previous.user),
                    password = updating.password.orElse(previous.password),
                    tags = updating.tags.getOrElse(previous.tags)
                  ))
            }

    }

  private[this] def hookBeforeDelete(implicit objectChecker: ObjectChecker,
                                     executionContext: ExecutionContext): HookBeforeDelete = (key: ObjectKey) =>
    objectChecker.checkList
      .allZookeepers()
      .allBrokers()
      .allWorkers()
      .allStreamApps()
      .node(key)
      .check()
      .map(
        report =>
          (report.nodes.head,
           report.zookeeperClusterInfos.keys.toSeq,
           report.brokerClusterInfos.keys.toSeq,
           report.workerClusterInfos.keys.toSeq,
           report.streamClusterInfos.keys.toSeq))
      .map {
        case (node, zookeeperClusterInfos, brokerClusterInfos, workerClusterInfos, streamClusterInfos) =>
          checkConflict(node.hostname, "zookeeper cluster", zookeeperClusterInfos)
          checkConflict(node.hostname, "broker cluster", brokerClusterInfos)
          checkConflict(node.hostname, "worker cluster", workerClusterInfos)
          checkConflict(node.hostname, "stream cluster", streamClusterInfos)
      }
      .recover {
        // the duplicate deletes are legal to ohara
        case e: ObjectCheckException if e.nonexistent.contains(key) => Unit
        case e: Throwable                                           => throw e
      }
      .map(_ => Unit)

  def apply(implicit store: DataStore,
            objectChecker: ObjectChecker,
            serviceCollie: ServiceCollie,
            executionContext: ExecutionContext): server.Route =
    RouteBuilder[Creation, Updating, Node]()
      .root(NODES_PREFIX_PATH)
      .hookOfCreation(hookOfCreation)
      .hookOfUpdating(hookOfUpdating)
      .hookOfGet(hookOfGet)
      .hookOfList(hookOfList)
      .hookBeforeDelete(hookBeforeDelete)
      .build()
}
