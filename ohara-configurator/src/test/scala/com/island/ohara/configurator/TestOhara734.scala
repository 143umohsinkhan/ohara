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
import com.island.ohara.client.configurator.v0.ConnectorApi
import com.island.ohara.common.data.{Column, DataType}
import com.island.ohara.common.rule.SmallTest
import org.junit.Test
import org.scalatest.Matchers

class TestOhara734 extends SmallTest with Matchers {

  @Test
  def testColumn(): Unit = {
    import spray.json._
    val request = ConnectorApi.COLUMN_JSON_FORMAT.read("""
                                                           |{
                                                           |  "name":"cf",
                                                           |  "dataType":"boolean",
                                                           |  "order":1
                                                           |}
                                                         """.stripMargin.parseJson)
    request.name shouldBe "cf"
    request.newName shouldBe "cf"
    request.dataType shouldBe DataType.BOOLEAN
    request.order shouldBe 1

    val request2 = ConnectorApi.COLUMN_JSON_FORMAT.read("""
                                                              |{
                                                              |  "name":"cf",
                                                              |  "newName":null,
                                                              |  "dataType":"boolean",
                                                              |  "order":1
                                                              |}
                                                           """.stripMargin.parseJson)
    request2 shouldBe request

    val request3 = ConnectorApi.COLUMN_JSON_FORMAT.read("""
                                                              |{
                                                              |  "name":"cf",
                                                              |  "newName":"cf",
                                                              |  "dataType":"boolean",
                                                              |  "order":1
                                                              |}
                                                            """.stripMargin.parseJson)
    request3 shouldBe request
  }

  @Test
  def testSourceRequest(): Unit = {
    import spray.json._
    val request =
      ConnectorApi.CONNECTOR_CREATION_REQUEST_JSON_FORMAT.read(
        """
                                                            |{
                                                            |  "name":"perf",
                                                            |  "className":"com.island.ohara.connector.perf.PerfSource",
                                                            |  "topics":["59e9010c-fd9c-4a41-918a-dacc9b84aa2b"],
                                                            |  "numberOfTasks":1,
                                                            |  "configs":{
                                                            |    "perf.batch":"1",
                                                            |    "perf.frequence":"2 seconds"
                                                            |  },
                                                            |  "schema":[{
                                                            |    "name": "cf0",
                                                            |    "newName": "cf0",
                                                            |    "dataType": "integer",
                                                            |    "order": 1
                                                            |  },{
                                                            |    "name": "cf1",
                                                            |    "newName": "cf1",
                                                            |    "dataType": "byte array",
                                                            |    "order": 2
                                                            |  }]
                                                            |}
                                                          """.stripMargin.parseJson)
    request.name.foreach(_ shouldBe "perf")
    request.className shouldBe "com.island.ohara.connector.perf.PerfSource"
    request.topics.head shouldBe "59e9010c-fd9c-4a41-918a-dacc9b84aa2b"
    request.numberOfTasks shouldBe 1
    request.configs("perf.batch") shouldBe "1"
    request.schema.size shouldBe 2
    request.schema.head shouldBe Column.of("cf0", "cf0", DataType.INT, 1)
    request.schema.last shouldBe Column.of("cf1", "cf1", DataType.BYTES, 2)

    val request2 =
      ConnectorApi.CONNECTOR_CREATION_REQUEST_JSON_FORMAT.read(
        """
                                                         |{
                                                         |  "name":"perf",
                                                         |  "className":"com.island.ohara.connector.perf.PerfSource",
                                                         |  "topics":["59e9010c-fd9c-4a41-918a-dacc9b84aa2b"],
                                                         |  "numberOfTasks":1,
                                                         |  "configs":{
                                                         |    "perf.batch":"1",
                                                         |    "perf.frequence":"2 seconds"
                                                         |  },
                                                         |  "schema":[{
                                                         |    "name": "cf0",
                                                         |    "newName": "cf0",
                                                         |    "dataType": "int",
                                                         |    "order": 1
                                                         |  },{
                                                         |    "name": "cf1",
                                                         |    "newName": "cf1",
                                                         |    "dataType": "bytes",
                                                         |    "order": 2
                                                         |  }]
                                                         |}
                                                       """.stripMargin.parseJson)
    request2 shouldBe request
  }

  @Test
  def testSinkRequest(): Unit = {
    import spray.json._
    val request =
      ConnectorApi.CONNECTOR_CREATION_REQUEST_JSON_FORMAT.read(
        """
                                                               |{
                                                               |  "name":"perf",
                                                               |  "className":"com.island.ohara.connector.perf.PerfSource",
                                                               |  "topics":["59e9010c-fd9c-4a41-918a-dacc9b84aa2b"],
                                                               |  "numberOfTasks":1,
                                                               |  "configs":{
                                                               |    "perf.batch":"1",
                                                               |    "perf.frequence":"2 seconds"
                                                               |  },
                                                               |  "schema":[{
                                                               |    "name": "cf0",
                                                               |    "newName": "cf0",
                                                               |    "dataType": "integer",
                                                               |    "order": 1
                                                               |  },{
                                                               |    "name": "cf1",
                                                               |    "newName": "cf1",
                                                               |    "dataType": "byte array",
                                                               |    "order": 2
                                                               |  }]
                                                               |}
                                                             """.stripMargin.parseJson)
    request.name.foreach(_ shouldBe "perf")
    request.className shouldBe "com.island.ohara.connector.perf.PerfSource"
    request.topics.head shouldBe "59e9010c-fd9c-4a41-918a-dacc9b84aa2b"
    request.numberOfTasks shouldBe 1
    request.configs("perf.batch") shouldBe "1"
    request.schema.size shouldBe 2
    request.schema.head shouldBe Column.of("cf0", "cf0", DataType.INT, 1)
    request.schema.last shouldBe Column.of("cf1", "cf1", DataType.BYTES, 2)

    val request2 =
      ConnectorApi.CONNECTOR_CREATION_REQUEST_JSON_FORMAT.read(
        """
                                                       |{
                                                       |  "name":"perf",
                                                       |  "className":"com.island.ohara.connector.perf.PerfSource",
                                                       |  "topics":["59e9010c-fd9c-4a41-918a-dacc9b84aa2b"],
                                                       |  "numberOfTasks":1,
                                                       |  "configs":{
                                                       |    "perf.batch":"1",
                                                       |    "perf.frequence":"2 seconds"
                                                       |  },
                                                       |  "schema":[{
                                                       |    "name": "cf0",
                                                       |    "newName": "cf0",
                                                       |    "dataType": "int",
                                                       |    "order": 1
                                                       |  },{
                                                       |    "name": "cf1",
                                                       |    "newName": "cf1",
                                                       |    "dataType": "bytes",
                                                       |    "order": 2
                                                       |  }]
                                                       |}
                                                     """.stripMargin.parseJson)
    request2 shouldBe request
  }
}
