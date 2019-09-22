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

package com.island.ohara.it.agent

import com.island.ohara.agent.ClusterCollie
import com.island.ohara.client.configurator.v0.{BrokerApi, ContainerApi, WorkerApi, ZookeeperApi}
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.{CommonUtils, Releasable}
import org.junit.After

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * this test implements all methods in BasicTestsOfCollie by ClusterCollie interface. Hence, there is no configurator
  * in test. If you want to test collie on e2e mode. Please extend TestSshClusterCollieByConfigurator.
  */
abstract class BasicTests4ClusterCollie extends BasicTests4Collie {
  protected def clusterCollie: ClusterCollie

  private[this] def zkCollie = clusterCollie.zookeeperCollie
  private[this] def bkCollie = clusterCollie.brokerCollie
  private[this] def wkCollie = clusterCollie.workerCollie

  /** to simplify test, we use the same group for ALL collie test
    * It is ok to use same group since we will use different cluster name
    */
  private[this] final val group: String = CommonUtils.randomString(10)

  //--------------------------------------------------[zk operations]--------------------------------------------------//
  override protected def zk_exist(clusterName: String): Future[Boolean] =
    zkCollie.exist(ObjectKey.of(group, clusterName))

  override protected def zk_create(clusterName: String,
                                   clientPort: Int,
                                   electionPort: Int,
                                   peerPort: Int,
                                   nodeNames: Set[String]): Future[ZookeeperApi.ZookeeperClusterInfo] =
    zkCollie.creator
      .imageName(ZookeeperApi.IMAGE_NAME_DEFAULT)
      .group(group)
      .name(clusterName)
      .clientPort(clientPort)
      .peerPort(peerPort)
      .electionPort(electionPort)
      .nodeNames(nodeNames)
      .create()

  override protected def zk_start(clusterName: String): Future[Unit] =
    // We don't need to start a cluster in collie since we already start a cluster by create method
    Future.successful(Unit)

  override protected def zk_stop(clusterName: String): Future[Unit] =
    zkCollie.forceRemove(ObjectKey.of(group, clusterName)).map(_ => Unit)

  override protected def zk_clusters(): Future[Seq[ZookeeperApi.ZookeeperClusterInfo]] =
    zkCollie.clusters().map(_.keys.toSeq)

  override protected def zk_logs(clusterName: String): Future[Seq[String]] =
    zkCollie.logs(ObjectKey.of(group, clusterName)).map(_.values.toSeq)

  override protected def zk_containers(clusterName: String): Future[Seq[ContainerApi.ContainerInfo]] =
    zkCollie.containers(ObjectKey.of(group, clusterName))

  override protected def zk_delete(clusterName: String): Future[Unit] =
    // We don't need to remove data stored in configurator in collie since there is nothing to do
    Future.successful(Unit)

  //--------------------------------------------------[bk operations]--------------------------------------------------//
  override protected def bk_exist(clusterName: String): Future[Boolean] =
    bkCollie.exist(ObjectKey.of(group, clusterName))

  override protected def bk_create(clusterName: String,
                                   clientPort: Int,
                                   exporterPort: Int,
                                   jmxPort: Int,
                                   zkClusterName: String,
                                   nodeNames: Set[String]): Future[BrokerApi.BrokerClusterInfo] =
    bkCollie.creator
      .imageName(BrokerApi.IMAGE_NAME_DEFAULT)
      .group(group)
      .name(clusterName)
      .clientPort(clientPort)
      .exporterPort(exporterPort)
      .jmxPort(jmxPort)
      .zookeeperClusterName(zkClusterName)
      .nodeNames(nodeNames)
      .create()

  override protected def bk_start(clusterName: String): Future[Unit] =
    // We don't need to start a cluster in collie since we already start a cluster by create method
    Future.successful(Unit)

  override protected def bk_stop(clusterName: String): Future[Unit] =
    bkCollie.forceRemove(ObjectKey.of(group, clusterName)).map(_ => Unit)

  override protected def bk_clusters(): Future[Seq[BrokerApi.BrokerClusterInfo]] = bkCollie.clusters().map(_.keys.toSeq)

  override protected def bk_logs(clusterName: String): Future[Seq[String]] =
    bkCollie.logs(ObjectKey.of(group, clusterName)).map(_.values.toSeq)

  override protected def bk_containers(clusterName: String): Future[Seq[ContainerApi.ContainerInfo]] =
    bkCollie.containers(ObjectKey.of(group, clusterName))

  override protected def bk_delete(clusterName: String): Future[Unit] =
    // We don't need to remove data stored in configurator in collie since there is nothing to do
    Future.successful(Unit)

  override protected def bk_addNode(clusterName: String, nodeName: String): Future[BrokerApi.BrokerClusterInfo] =
    bkCollie
      .cluster(clusterName)
      .map(_._1)
      .flatMap(cluster => bkCollie.creator.settings(cluster.settings).nodeName(nodeName).create())

  override protected def bk_removeNode(clusterName: String, nodeName: String): Future[Unit] =
    bkCollie.removeNode(ObjectKey.of(group, clusterName), nodeName).map(_ => Unit)

  //--------------------------------------------------[wk operations]--------------------------------------------------//
  override protected def wk_exist(clusterName: String): Future[Boolean] =
    wkCollie.exist(ObjectKey.of(group, clusterName))

  override protected def wk_create(clusterName: String,
                                   clientPort: Int,
                                   jmxPort: Int,
                                   bkClusterName: String,
                                   nodeNames: Set[String]): Future[WorkerApi.WorkerClusterInfo] =
    wkCollie.creator
      .imageName(WorkerApi.IMAGE_NAME_DEFAULT)
      .name(clusterName)
      .group(group)
      .clientPort(clientPort)
      .jmxPort(jmxPort)
      .brokerClusterName(bkClusterName)
      .groupId(CommonUtils.randomString(10))
      .configTopicName(CommonUtils.randomString(10))
      .configTopicReplications(1)
      .statusTopicName(CommonUtils.randomString(10))
      .statusTopicReplications(1)
      .statusTopicPartitions(1)
      .offsetTopicName(CommonUtils.randomString(10))
      .offsetTopicReplications(1)
      .offsetTopicPartitions(1)
      .nodeNames(nodeNames)
      .create()

  override protected def wk_create(clusterName: String,
                                   clientPort: Int,
                                   jmxPort: Int,
                                   groupId: String,
                                   configTopicName: String,
                                   statusTopicName: String,
                                   offsetTopicName: String,
                                   bkClusterName: String,
                                   nodeNames: Set[String]): Future[WorkerApi.WorkerClusterInfo] =
    wkCollie.creator
      .imageName(WorkerApi.IMAGE_NAME_DEFAULT)
      .name(clusterName)
      .group(group)
      .clientPort(clientPort)
      .jmxPort(jmxPort)
      .brokerClusterName(bkClusterName)
      .groupId(groupId)
      .configTopicName(configTopicName)
      .configTopicReplications(1)
      .statusTopicName(statusTopicName)
      .statusTopicReplications(1)
      .statusTopicPartitions(1)
      .offsetTopicName(offsetTopicName)
      .offsetTopicReplications(1)
      .offsetTopicPartitions(1)
      .nodeNames(nodeNames)
      .create()

  override protected def wk_start(clusterName: String): Future[Unit] =
    // We don't need to start a cluster in collie since we already start a cluster by create method
    Future.successful(Unit)

  override protected def wk_stop(clusterName: String): Future[Unit] =
    wkCollie.forceRemove(ObjectKey.of(group, clusterName)).map(_ => Unit)

  override protected def wk_clusters(): Future[Seq[WorkerApi.WorkerClusterInfo]] = wkCollie.clusters().map(_.keys.toSeq)

  override protected def wk_logs(clusterName: String): Future[Seq[String]] =
    wkCollie.logs(ObjectKey.of(group, clusterName)).map(_.values.toSeq)

  override protected def wk_containers(clusterName: String): Future[Seq[ContainerApi.ContainerInfo]] =
    wkCollie.containers(ObjectKey.of(group, clusterName))

  override protected def wk_delete(clusterName: String): Future[Unit] =
    // We don't need to remove data stored in configurator in collie since there is nothing to do
    Future.successful(Unit)

  override protected def wk_addNode(clusterName: String, nodeName: String): Future[WorkerApi.WorkerClusterInfo] =
    wkCollie
      .cluster(clusterName)
      .map(_._1)
      .flatMap(cluster => wkCollie.creator.settings(cluster.settings).nodeName(nodeName).create())

  override protected def wk_removeNode(clusterName: String, nodeName: String): Future[Unit] =
    wkCollie.removeNode(ObjectKey.of(group, clusterName), nodeName).map(_ => Unit)

  @After
  final def tearDown(): Unit = Releasable.close(clusterCollie)
}
