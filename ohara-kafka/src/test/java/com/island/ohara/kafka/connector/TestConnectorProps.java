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

package com.island.ohara.kafka.connector;

import com.island.ohara.common.data.Cell;
import com.island.ohara.common.data.Column;
import com.island.ohara.common.data.DataType;
import com.island.ohara.common.data.Row;
import com.island.ohara.common.data.Serializer;
import com.island.ohara.common.rule.OharaTest;
import com.island.ohara.common.setting.ConnectorKey;
import com.island.ohara.common.setting.SettingDef;
import com.island.ohara.common.util.CommonUtils;
import com.island.ohara.kafka.connector.json.ConnectorFormatter;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.Assert;
import org.junit.Test;

public class TestConnectorProps extends OharaTest {

  @Test
  public void emptyInStartingSource() {
    DumbSource connector = new DumbSource();
    // ohara auto-fill the keys with default values
    connector.start(Collections.emptyMap());
  }

  @Test
  public void emptyInStartingSink() {
    DumbSink connector = new DumbSink();
    // ohara auto-fill the keys with default values
    connector.start(Collections.emptyMap());
  }

  @Test
  public void emptyInStartingSourceTask() {
    DumbSourceTask task = new DumbSourceTask();
    assertException(NoSuchElementException.class, () -> task.start(Collections.emptyMap()));
  }

  @Test
  public void emptyInStartingSinkTask() {
    DumbSinkTask task = new DumbSinkTask();
    assertException(NoSuchElementException.class, () -> task.start(Collections.emptyMap()));
  }

  @Test
  public void testCounterInSink() {
    Column column =
        Column.builder()
            .name(CommonUtils.randomString(10))
            .dataType(DataType.STRING)
            .order(1)
            .build();
    RowSinkTask task = new DumbSinkTask();
    ConnectorKey connectorKey = ConnectorKey.of("g", "n");
    // we call start to initialize counter.
    task.start(
        ConnectorFormatter.of()
            .connectorKey(connectorKey)
            .checkRule(SettingDef.CheckRule.PERMISSIVE)
            .column(column)
            .raw());
    try {
      Assert.assertNotNull(task.messageNumberCounter);
      Assert.assertNotNull(task.messageSizeCounter);
      Assert.assertNotNull(task.ignoredMessageSizeCounter);
      Assert.assertNotNull(task.ignoredMessageNumberCounter);

      Assert.assertEquals(task.messageNumberCounter.group(), connectorKey.connectorNameOnKafka());
      Assert.assertEquals(task.messageSizeCounter.group(), connectorKey.connectorNameOnKafka());
      Assert.assertEquals(
          task.ignoredMessageSizeCounter.group(), connectorKey.connectorNameOnKafka());
      Assert.assertEquals(
          task.ignoredMessageNumberCounter.group(), connectorKey.connectorNameOnKafka());

      Assert.assertEquals(task.messageNumberCounter.getValue(), 0);
      Assert.assertEquals(task.messageSizeCounter.getValue(), 0);
      Assert.assertEquals(task.ignoredMessageSizeCounter.getValue(), 0);
      Assert.assertEquals(task.ignoredMessageNumberCounter.getValue(), 0);

      // add legal data
      task.put(
          Collections.singletonList(
              new SinkRecord(
                  "topic",
                  0,
                  null,
                  Serializer.ROW.to(Row.of(Cell.of(column.name(), CommonUtils.randomString()))),
                  null,
                  null,
                  10)));
      Assert.assertEquals(task.messageNumberCounter.getValue(), 1);
      Assert.assertNotEquals(task.messageSizeCounter.getValue(), 0);
      Assert.assertEquals(task.ignoredMessageNumberCounter.getValue(), 0);
      Assert.assertEquals(task.ignoredMessageSizeCounter.getValue(), 0);

      // add illegal data
      task.put(
          Collections.singletonList(
              new SinkRecord(
                  "topic",
                  0,
                  null,
                  Serializer.ROW.to(Row.of(Cell.of(column.name(), 12313))),
                  null,
                  null,
                  10)));
      Assert.assertEquals(task.messageNumberCounter.getValue(), 1);
      Assert.assertNotEquals(task.messageSizeCounter.getValue(), 0);
      Assert.assertEquals(task.ignoredMessageNumberCounter.getValue(), 1);
      Assert.assertNotEquals(task.ignoredMessageSizeCounter.getValue(), 0);

    } finally {
      task.stop();
      Assert.assertTrue(task.messageNumberCounter.isClosed());
      Assert.assertTrue(task.messageSizeCounter.isClosed());
      Assert.assertTrue(task.ignoredMessageSizeCounter.isClosed());
      Assert.assertTrue(task.ignoredMessageNumberCounter.isClosed());
    }

    RowSinkTask task2 = new DumbSinkTask();
    try {
      // we call start to initialize counter.
      task2.start(
          ConnectorFormatter.of()
              .connectorKey(ConnectorKey.of("g", "n"))
              .checkRule(SettingDef.CheckRule.ENFORCING)
              .column(column)
              .raw());
      assertException(
          IllegalArgumentException.class,
          () ->
              task2.put(
                  Collections.singletonList(
                      new SinkRecord(
                          "topic",
                          0,
                          null,
                          Row.of(Cell.of(column.name(), 12313)),
                          null,
                          null,
                          10))));
    } finally {
      task2.stop();
    }
  }

  @Test
  public void testCounterInSource() {
    Column column =
        Column.builder()
            .name(CommonUtils.randomString(10))
            .dataType(DataType.STRING)
            .order(1)
            .build();
    Row goodRow = Row.of(Cell.of(column.newName(), CommonUtils.randomString()));
    Row badRow = Row.of(Cell.of(column.newName(), 123123));
    RowSourceTask task =
        new DumbSourceTask() {
          private boolean good = true;

          @Override
          protected List<RowSourceRecord> _poll() {
            try {
              return Collections.singletonList(
                  RowSourceRecord.builder()
                      .row(good ? goodRow : badRow)
                      .topicName(CommonUtils.randomString(10))
                      .build());
            } finally {
              good = false;
            }
          }
        };
    ConnectorKey connectorKey = ConnectorKey.of("g", "n");
    // we call start to initialize counter.
    task.start(
        ConnectorFormatter.of()
            .connectorKey(connectorKey)
            .checkRule(SettingDef.CheckRule.PERMISSIVE)
            .column(column)
            .raw());
    try {
      Assert.assertNotNull(task.messageNumberCounter);
      Assert.assertNotNull(task.messageSizeCounter);
      Assert.assertNotNull(task.ignoredMessageNumberCounter);
      Assert.assertNotNull(task.ignoredMessageSizeCounter);

      Assert.assertEquals(task.messageNumberCounter.group(), connectorKey.connectorNameOnKafka());
      Assert.assertEquals(task.messageSizeCounter.group(), connectorKey.connectorNameOnKafka());
      Assert.assertEquals(
          task.ignoredMessageNumberCounter.group(), connectorKey.connectorNameOnKafka());
      Assert.assertEquals(
          task.ignoredMessageSizeCounter.group(), connectorKey.connectorNameOnKafka());

      Assert.assertEquals(task.messageNumberCounter.getValue(), 0);
      Assert.assertEquals(task.messageSizeCounter.getValue(), 0);
      Assert.assertEquals(task.ignoredMessageNumberCounter.getValue(), 0);
      Assert.assertEquals(task.ignoredMessageSizeCounter.getValue(), 0);

      task.poll();
      Assert.assertEquals(task.messageNumberCounter.getValue(), 1);
      Assert.assertNotEquals(task.messageSizeCounter.getValue(), 0);
      Assert.assertEquals(task.ignoredMessageNumberCounter.getValue(), 0);
      Assert.assertEquals(task.ignoredMessageSizeCounter.getValue(), 0);

      // this poll generates bad data
      task.poll();
      Assert.assertEquals(task.messageNumberCounter.getValue(), 1);
      Assert.assertNotEquals(task.messageSizeCounter.getValue(), 0);
      Assert.assertEquals(task.ignoredMessageNumberCounter.getValue(), 1);
      Assert.assertNotEquals(task.ignoredMessageSizeCounter.getValue(), 0);

    } finally {
      task.stop();
      Assert.assertTrue(task.messageNumberCounter.isClosed());
      Assert.assertTrue(task.messageSizeCounter.isClosed());
      Assert.assertTrue(task.ignoredMessageNumberCounter.isClosed());
      Assert.assertTrue(task.ignoredMessageSizeCounter.isClosed());
    }

    RowSourceTask task2 =
        new DumbSourceTask() {
          @Override
          protected List<RowSourceRecord> _poll() {
            return Collections.singletonList(
                RowSourceRecord.builder()
                    .row(badRow)
                    .topicName(CommonUtils.randomString(10))
                    .build());
          }
        };

    try {
      // we call start to initialize counter.
      task2.start(
          ConnectorFormatter.of()
              .connectorKey(connectorKey)
              .checkRule(SettingDef.CheckRule.ENFORCING)
              .column(column)
              .raw());
      // this poll generates bad data and the check rule is "enforcing"
      assertException(IllegalArgumentException.class, task2::poll);
    } finally {
      task2.stop();
    }

    RowSourceTask task3 =
        new DumbSourceTask() {
          @Override
          protected List<RowSourceRecord> _poll() {
            return Collections.singletonList(
                RowSourceRecord.builder()
                    .row(badRow)
                    .topicName(CommonUtils.randomString(10))
                    .build());
          }
        };

    try {
      // we call start to initialize counter.
      task3.start(
          ConnectorFormatter.of()
              .connectorKey(connectorKey)
              .checkRule(SettingDef.CheckRule.NONE)
              .column(column)
              .raw());
      // this poll generates bad data and the check rule is "enforcing"
      task3.poll();
      Assert.assertEquals(task3.messageNumberCounter.getValue(), 1);
      Assert.assertNotEquals(task3.messageSizeCounter.getValue(), 0);
      Assert.assertEquals(task3.ignoredMessageNumberCounter.getValue(), 0);
      Assert.assertEquals(task3.ignoredMessageSizeCounter.getValue(), 0);
    } finally {
      task3.stop();
    }
  }

  @Test
  public void testStop() {
    RowSourceTask task = new DumbSourceTask();
    // we don't call start() so all internal counters should be null
    task.stop();
  }

  @Test
  public void testInternalTaskConfigOfSource() {
    RowSourceConnector connector = new DumbSource();
    Assert.assertNull(connector.taskSetting);
    connector.start(ConnectorFormatter.of().connectorKey(ConnectorKey.of("g", "n")).raw());
    Assert.assertNotNull(connector.taskSetting);
  }

  @Test
  public void testInternalTaskConfigOfSourceTask() {
    RowSourceTask task = new DumbSourceTask();
    Assert.assertNull(task.taskSetting);

    task.start(ConnectorFormatter.of().connectorKey(ConnectorKey.of("g", "n")).raw());
    Assert.assertNotNull(task.taskSetting);
  }

  @Test
  public void testInternalObjectKeyOfSourceTask() {
    RowSourceTask task = new DumbSourceTask();
    Assert.assertNull(task.keyInBytes);

    task.start(ConnectorFormatter.of().connectorKey(ConnectorKey.of("g", "n")).raw());
    Assert.assertNotNull(task.keyInBytes);
  }

  @Test
  public void testInternalTaskConfigOfSink() {
    RowSinkConnector connector = new DumbSink();
    Assert.assertNull(connector.taskSetting);

    connector.start(ConnectorFormatter.of().connectorKey(ConnectorKey.of("g", "n")).raw());
    Assert.assertNotNull(connector.taskSetting);
  }

  @Test
  public void testInternalTaskConfigOfSinkTask() {
    RowSinkTask task = new DumbSinkTask();
    Assert.assertNull(task.taskSetting);

    task.start(ConnectorFormatter.of().connectorKey(ConnectorKey.of("g", "n")).raw());
    Assert.assertNotNull(task.taskSetting);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failToCallCounterBuilderBeforeStartingSource() {
    new DumbSource().counterBuilder();
  }

  @Test(expected = IllegalArgumentException.class)
  public void failToCallCounterBuilderBeforeStartingSink() {
    new DumbSink().counterBuilder();
  }

  @Test(expected = IllegalArgumentException.class)
  public void failToCallCounterBuilderBeforeStartingSourceTask() {
    new DumbSourceTask().counterBuilder();
  }

  @Test(expected = IllegalArgumentException.class)
  public void failToCallCounterBuilderBeforeStartingSinkTask() {
    new DumbSinkTask().counterBuilder();
  }
}
