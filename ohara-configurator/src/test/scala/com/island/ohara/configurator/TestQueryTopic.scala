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

package com.island.ohara.configurator

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{CountDownLatch, TimeUnit}

import com.island.ohara.client.configurator.v0.{BrokerApi, QueryApi, TopicApi}
import com.island.ohara.common.data.{Cell, Row, Serializer}
import com.island.ohara.common.util.{CommonUtils, Releasable}
import com.island.ohara.kafka.Producer
import com.island.ohara.testing.WithBrokerWorker
import org.junit.{After, Test}
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class TestQueryTopic extends WithBrokerWorker with Matchers {

  private[this] val configurator =
    Configurator.builder.fake(testUtil().brokersConnProps(), testUtil().workersConnProps()).build()

  private[this] val brokerClusterInfo = result(
    BrokerApi.access.hostname(configurator.hostname).port(configurator.port).list()).head

  private[this] def topicApi = TopicApi.access.hostname(configurator.hostname).port(configurator.port)
  private[this] def queryApi = QueryApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] def result[T](f: Future[T]): T = Await.result(f, 20 seconds)

  @Test
  def goodCase(): Unit = {
    val topicInfo = result(topicApi.request.brokerClusterKey(brokerClusterInfo.key).create())
    result(topicApi.start(topicInfo.key))

    val closed = new AtomicBoolean(false)

    val latch = new CountDownLatch(1)
    Future {
      val producer =
        Producer.builder().connectionProps(brokerClusterInfo.connectionProps).keySerializer(Serializer.ROW).build()
      try while (!closed.get()) {
        producer
          .sender()
          .topicName(topicInfo.key.topicNameOnKafka())
          .key(Row.of(Cell.of(CommonUtils.randomString(5), CommonUtils.randomString(5))))
          .send()
          .get()
        TimeUnit.MILLISECONDS.sleep(300)
      } finally {
        Releasable.close(producer)
        latch.countDown()
      }
    }

    try {
      val messages = result(queryApi.topicRequest.key(topicInfo.key).query()).messages
      messages should not be Seq.empty
      messages.foreach { message =>
        // the data is not generated by connector so it has no source key.
        message.sourceKey shouldBe None
        message.value should not be None
        message.error shouldBe None
      }
      result(queryApi.topicRequest.key(topicInfo.key).limit(1).query()).messages.size should be >= 1
      result(queryApi.topicRequest.key(topicInfo.key).limit(2).query()).messages.size should be >= 2
    } finally {
      closed.set(true)
      latch.await()
    }
  }

  @Test
  def badCase(): Unit = {
    val topicInfo = result(topicApi.request.brokerClusterKey(brokerClusterInfo.key).create())
    result(topicApi.start(topicInfo.key))

    val closed = new AtomicBoolean(false)

    val latch = new CountDownLatch(1)
    Future {
      val producer =
        Producer.builder().connectionProps(brokerClusterInfo.connectionProps).keySerializer(Serializer.STRING).build()
      try while (!closed.get()) {
        producer.sender().topicName(topicInfo.key.topicNameOnKafka()).key(CommonUtils.randomString(5)).send().get()
        TimeUnit.MILLISECONDS.sleep(300)
      } finally {
        Releasable.close(producer)
        latch.countDown()
      }
    }

    try {
      val messages = result(queryApi.topicRequest.key(topicInfo.key).query()).messages
      messages.size should be > 0
      messages.foreach { message =>
        message.value shouldBe None
        message.error should not be None
      }
    } finally {
      closed.set(true)
      latch.await()
    }
  }

  @After
  def tearDown(): Unit = Releasable.close(configurator)
}
