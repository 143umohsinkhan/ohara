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

package com.island.ohara.agent.ssh

import java.util.concurrent.ExecutorService

import com.island.ohara.agent._
import com.island.ohara.agent.container.ContainerName
import com.island.ohara.agent.docker.DockerClient
import com.island.ohara.client.configurator.v0.ContainerApi.ContainerInfo
import com.island.ohara.client.configurator.v0.NodeApi.{Node, Resource}
import com.island.ohara.client.configurator.v0.{BrokerApi, ClusterStatus, StreamApi, WorkerApi, ZookeeperApi}
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.Releasable

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

// accessible to configurator
private[ohara] class ServiceCollieImpl(cacheTimeout: Duration, dataCollie: DataCollie, cacheThreadPool: ExecutorService)
    extends ServiceCollie {
  private[this] val dockerCache = DockerClientCache()

  private[this] val clusterCache: ServiceCache = ServiceCache.builder
    .frequency(cacheTimeout)
    // TODO: 5 * timeout is enough ??? by chia
    .supplier(() => Await.result(doClusters(ExecutionContext.fromExecutor(cacheThreadPool)), cacheTimeout * 5))
    // Giving some time to process to complete the build and then we can remove it from cache safety.
    .lazyRemove(cacheTimeout)
    .build()

  override val zookeeperCollie: ZookeeperCollie = new ZookeeperCollieImpl(dataCollie, dockerCache, clusterCache)
  override val brokerCollie: BrokerCollie       = new BrokerCollieImpl(dataCollie, dockerCache, clusterCache)
  override val workerCollie: WorkerCollie       = new WorkerCollieImpl(dataCollie, dockerCache, clusterCache)
  override val streamCollie: StreamCollie       = new StreamCollieImpl(dataCollie, dockerCache, clusterCache)

  private[this] def doClusters(
    implicit executionContext: ExecutionContext
  ): Future[Seq[ClusterStatus]] =
    dataCollie
      .values[Node]()
      .flatMap(
        Future
          .traverse(_) { node =>
            // multi-thread to seek all containers from multi-nodes
            // Note: we fetch all containers (include exited and running) here
            dockerCache.exec(node, _.containers()).recover {
              case e: Throwable =>
                LOG.error(s"failed to get active containers from $node", e)
                Seq.empty
            }
          }
          .map(_.flatten)
      )
      .flatMap { allContainers =>
        def parse(
          serviceName: String,
          service: ClusterStatus.Kind,
          f: (ObjectKey, Seq[ContainerInfo]) => Future[ClusterStatus]
        ): Future[Seq[ClusterStatus]] =
          Future
            .sequence(
              allContainers
                .filter(container => Collie.matched(container.name, serviceName))
                .map(container => Collie.objectKeyOfContainerName(container.name) -> container)
                .groupBy(_._1)
                .map {
                  case (clusterKey, value) => clusterKey -> value.map(_._2)
                }
                .map {
                  case (clusterKey, containers) => f(clusterKey, containers)
                }
            )
            .map(_.toSeq)

        for {
          zkMap     <- parse(ZookeeperApi.ZOOKEEPER_SERVICE_NAME, ClusterStatus.Kind.ZOOKEEPER, zookeeperCollie.toStatus)
          bkMap     <- parse(BrokerApi.BROKER_SERVICE_NAME, ClusterStatus.Kind.BROKER, brokerCollie.toStatus)
          wkMap     <- parse(WorkerApi.WORKER_SERVICE_NAME, ClusterStatus.Kind.WORKER, workerCollie.toStatus)
          streamMap <- parse(StreamApi.STREAM_SERVICE_NAME, ClusterStatus.Kind.STREAM, streamCollie.toStatus)
        } yield zkMap ++ bkMap ++ wkMap ++ streamMap
      }

  override def close(): Unit = {
    Releasable.close(dockerCache)
    Releasable.close(clusterCache)
    Releasable.close(() => cacheThreadPool.shutdownNow())
  }

  override def images()(implicit executionContext: ExecutionContext): Future[Map[Node, Seq[String]]] =
    dataCollie.values[Node]().flatMap { nodes =>
      Future.traverse(nodes)(node => Future(dockerCache.exec(node, node -> _.imageNames()))).map(_.toMap)
    }

  /**
    * The default implementation has the following checks.
    * 1) run hello-world image
    * 2) check existence of hello-world
    */
  override def verifyNode(node: Node)(implicit executionContext: ExecutionContext): Future[String] =
    Future {
      val dockerClient =
        DockerClient(
          Agent.builder.hostname(node.hostname).port(node._port).user(node._user).password(node._password).build
        )
      try if (dockerClient.resources().isEmpty)
        throw new IllegalStateException(s"the docker on ${node.hostname} is unavailable")
      else s"succeed to check the docker resources on ${node.name}"
      finally Releasable.close(dockerClient)
    }.recover {
      case e: IllegalStateException => throw e
      case e: Throwable =>
        e.printStackTrace()
        throw new IllegalStateException(s"failed to verify ${node.hostname} since ${e.getMessage}", e)
    }

  override def containerNames()(implicit executionContext: ExecutionContext): Future[Seq[ContainerName]] =
    dataCollie.values[Node]().map(_.flatMap(dockerCache.exec(_, _.containerNames())))

  override def log(name: String, sinceSeconds: Option[Long])(
    implicit executionContext: ExecutionContext
  ): Future[(ContainerName, String)] =
    dataCollie
      .values[Node]()
      .map(
        _.flatMap(
          node =>
            dockerCache.exec(
              node,
              client =>
                client.containerNames().filter(_.name == name).flatMap { containerName =>
                  try Some((containerName, client.log(containerName.name, sinceSeconds)))
                  catch {
                    case _: Throwable => None
                  }
                }
            )
        ).head
      )

  override def resources()(implicit executionContext: ExecutionContext): Future[Map[Node, Seq[Resource]]] =
    dataCollie
      .values[Node]()
      .map(
        _.map(
          node =>
            node -> dockerCache.exec(
              node,
              _.resources()
            )
        ).toMap
      )
}
