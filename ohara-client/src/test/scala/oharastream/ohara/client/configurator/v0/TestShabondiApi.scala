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

package oharastream.ohara.client.configurator.v0

import oharastream.ohara.client.configurator.v0.ShabondiApi.SOURCE_ALL_DEFINITIONS
import oharastream.ohara.common.rule.OharaTest
import oharastream.ohara.shabondi.ShabondiDefinitions._
import org.junit.Test
import org.scalatest.matchers.should.Matchers._
import spray.json._

class TestShabondiApi extends OharaTest {
  @Test
  def testSourceDefinitions(): Unit =
    ShabondiApi.SHABONDI_CLUSTER_CREATION_JSON_FORMAT
      .read(s"""
                |  {
                |    "group": "g",
                |    "name": "n",
                |    "${CLIENT_PORT_DEFINITION.key()}": 123,
                |    "${BROKER_CLUSTER_KEY_DEFINITION.key()}": {
                |      "group": "b",
                |      "name": "n"
                |    },
                |    "${NODE_NAMES_DEFINITION.key()}": ["nn"],
                |    "${SHABONDI_CLASS_DEFINITION.key()}": "${ShabondiApi.SHABONDI_SOURCE_CLASS_NAME}"
                |  }
                |""".stripMargin.parseJson)
      .definitions shouldBe SOURCE_ALL_DEFINITIONS

  @Test
  def testSinkDefinitions(): Unit =
    ShabondiApi.SHABONDI_CLUSTER_CREATION_JSON_FORMAT
      .read(s"""
                |  {
                |    "group": "g",
                |    "name": "n",
                |    "${CLIENT_PORT_DEFINITION.key()}": 123,
                |    "${BROKER_CLUSTER_KEY_DEFINITION.key()}": {
                |      "group": "b",
                |      "name": "n"
                |    },
                |    "${NODE_NAMES_DEFINITION.key()}": ["nn"],
                |    "${SHABONDI_CLASS_DEFINITION.key()}": "${ShabondiApi.SHABONDI_SINK_CLASS_NAME}"
                |  }
                |""".stripMargin.parseJson)
      .definitions shouldBe SOURCE_ALL_DEFINITIONS
}
