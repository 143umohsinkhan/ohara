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

package oharastream.ohara.connector.jdbc.source

import java.sql.Statement

import oharastream.ohara.client.configurator.v0.InspectApi.RdbColumn
import oharastream.ohara.client.database.DatabaseClient
import oharastream.ohara.common.data.{Column, DataType}
import oharastream.ohara.common.rule.OharaTest
import oharastream.ohara.common.setting.TopicKey
import oharastream.ohara.common.util.Releasable
import oharastream.ohara.testing.service.Database
import org.junit.{Before, Test}
import oharastream.ohara.kafka.connector.{RowSourceRecord, TaskSetting}
import org.apache.kafka.connect.source.SourceTaskContext
import org.apache.kafka.connect.storage.OffsetStorageReader
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers._

import scala.jdk.CollectionConverters._

class TestJDBCSourceTaskOffset extends OharaTest {
  private[this] val db                  = Database.local()
  private[this] val client              = DatabaseClient.builder.url(db.url()).user(db.user()).password(db.password()).build
  private[this] val tableName           = "TABLE1"
  private[this] val timestampColumnName = "COLUMN1"

  private[this] val jdbcSourceTask: JDBCSourceTask           = new JDBCSourceTask()
  private[this] val taskContext: SourceTaskContext           = Mockito.mock(classOf[SourceTaskContext])
  private[this] val taskSetting: TaskSetting                 = Mockito.mock(classOf[TaskSetting])
  private[this] val offsetStorageReader: OffsetStorageReader = Mockito.mock(classOf[OffsetStorageReader])
  @Before
  def setup(): Unit = {
    val column2 = "COLUMN2"
    val dbColumns = Seq(
      RdbColumn(timestampColumnName, "TIMESTAMP(6)", false),
      RdbColumn(column2, "varchar(45)", true)
    )
    client.createTable(tableName, dbColumns)
    val statement: Statement = db.connection.createStatement()
    try {
      statement.executeUpdate(
        s"INSERT INTO $tableName($timestampColumnName, column2) VALUES('2018-09-01 00:00:00', '1')"
      )
      (2 to 8).foreach { i =>
        val sql = s"INSERT INTO $tableName($timestampColumnName, column2) VALUES('2018-09-01 00:00:01', '$i')"
        statement.executeUpdate(sql)
      }

      // Mock JDBC Source Task
      when(taskContext.offsetStorageReader()).thenReturn(offsetStorageReader)
      jdbcSourceTask.initialize(taskContext.asInstanceOf[SourceTaskContext])

      when(taskSetting.stringValue(DB_URL)).thenReturn(db.url)
      when(taskSetting.stringValue(DB_USERNAME)).thenReturn(db.user)
      when(taskSetting.stringValue(DB_PASSWORD)).thenReturn(db.password)
      when(taskSetting.stringValue(DB_TABLENAME)).thenReturn(tableName)
      when(taskSetting.stringOption(DB_SCHEMA_PATTERN)).thenReturn(java.util.Optional.empty[String]())
      when(taskSetting.stringOption(DB_CATALOG_PATTERN)).thenReturn(java.util.Optional.empty[String]())
      when(taskSetting.stringOption(MODE)).thenReturn(java.util.Optional.empty[String]())
      when(taskSetting.stringValue(TIMESTAMP_COLUMN_NAME)).thenReturn(timestampColumnName)
      when(taskSetting.intOption(JDBC_FETCHDATA_SIZE))
        .thenReturn(java.util.Optional.of(java.lang.Integer.valueOf(2000)))
      when(taskSetting.intOption(JDBC_FLUSHDATA_SIZE))
        .thenReturn(java.util.Optional.of(java.lang.Integer.valueOf(2000)))
      when(taskSetting.durationOption(JDBC_FREQUENCE_TIME))
        .thenReturn(java.util.Optional.of(java.time.Duration.ofMillis(0)))

      val columns: Seq[Column] = Seq(
        Column.builder().name(timestampColumnName).dataType(DataType.OBJECT).order(0).build(),
        Column.builder().name(column2).dataType(DataType.STRING).order(1).build()
      )

      when(taskSetting.columns).thenReturn(columns.asJava)
      when(taskSetting.topicKeys()).thenReturn(Set(TopicKey.of("g", "topic1")).asJava)
    } finally Releasable.close(statement)
  }

  @Test
  def testOffset(): Unit = {
    val maps: Map[String, Object] = Map("db.table.offset" -> "2018-09-01 00:00:00.0,3")
    when(offsetStorageReader.offset(Map("db.table.name" -> tableName).asJava)).thenReturn(maps.asJava)
    jdbcSourceTask.run(taskSetting)
    val rows: Seq[RowSourceRecord] = jdbcSourceTask.pollRecords().asScala.toSeq
    rows.size shouldBe 4
    rows(0).sourceOffset.asScala.foreach { x =>
      x._1 shouldBe JDBCSourceTask.DB_TABLE_OFFSET_KEY
      x._2 shouldBe "2018-09-01 00:00:00.0,4"
    }

    rows(1).sourceOffset.asScala.foreach { x =>
      x._1 shouldBe JDBCSourceTask.DB_TABLE_OFFSET_KEY
      x._2 shouldBe "2018-09-01 00:00:00.0,5"
    }

    rows(2).sourceOffset.asScala.foreach { x =>
      x._1 shouldBe JDBCSourceTask.DB_TABLE_OFFSET_KEY
      x._2 shouldBe "2018-09-01 00:00:00.0,6"
    }

    rows(3).sourceOffset.asScala.foreach { x =>
      x._1 shouldBe JDBCSourceTask.DB_TABLE_OFFSET_KEY
      x._2 shouldBe "2018-09-01 00:00:00.0,7"
    }
  }
}
