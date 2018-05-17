package com.island.ohara.integration

import java.io.{DataInputStream, OutputStream}

import com.island.ohara.io.ByteUtil
import com.island.ohara.io.CloseOnce._
import com.island.ohara.rule.LargeTest
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer}
import org.junit.Test
import org.scalatest.Matchers

import scala.concurrent.duration._

class TestOharaTestUtil extends LargeTest with Matchers {

  @Test
  def testCreateClusterWithMultiBrokers(): Unit = {
    doClose(new OharaTestUtil(3)) { testUtil =>
      {
        testUtil.kafkaBrokers.size shouldBe 3
        testUtil.createTopic("my_topic")
        testUtil.exist("my_topic") shouldBe true
        val (_, valueQueue) = testUtil.run("my_topic", new ByteArrayDeserializer, new ByteArrayDeserializer)
        val totalMessageCount = 100
        doClose(
          new KafkaProducer[Array[Byte], Array[Byte]](testUtil.properties,
                                                      new ByteArraySerializer,
                                                      new ByteArraySerializer)) { producer =>
          {
            var count: Int = totalMessageCount
            while (count > 0) {
              producer.send(
                new ProducerRecord[Array[Byte], Array[Byte]]("my_topic",
                                                             ByteUtil.toBytes("key"),
                                                             ByteUtil.toBytes("value")))
              count -= 1
            }
          }
        }
        testUtil.await(() => valueQueue.size() == totalMessageCount, 1 minute)
        valueQueue.forEach((value: Array[Byte]) => ByteUtil.toString(value) shouldBe "value")
      }
    }
  }

  @Test
  def testCreateConnectorWithMultiWorkers(): Unit = {
    val sourceTasks = 3
    val sinkTasks = 2
    doClose(new OharaTestUtil(3, 2)) { testUtil =>
      {
        testUtil.availableConnectors().contains(classOf[SimpleSourceConnector].getSimpleName) shouldBe true
        testUtil.runningConnectors() shouldBe "[]"
        var resp = testUtil.startConnector(s"""{"name":"my_source_connector", "config":{"connector.class":"${classOf[
          SimpleSourceConnector].getName}","topic":"my_connector_topic","tasks.max":"$sourceTasks"}}""")
        withClue(s"body:${resp._2}") {
          resp._1 shouldBe 201
        }
        // wait for starting the source connector
        testUtil.await(() => testUtil.runningConnectors().contains("my_source_connector"), 10 second)
        // wait for starting the source task
        testUtil.await(() => SimpleSourceTask.taskCount.get >= sourceTasks, 10 second)
        resp = testUtil.startConnector(s"""{"name":"my_sink_connector", "config":{"connector.class":"${classOf[
          SimpleSinkConnector].getName}","topics":"my_connector_topic","tasks.max":"$sinkTasks"}}""")
        withClue(s"body:${resp._2}") {
          resp._1 shouldBe 201
        }
        // wait for starting the sink connector
        testUtil.await(() => testUtil.runningConnectors().contains("my_sink_connector"), 10 second)
        // wait for starting the sink task
        testUtil.await(() => SimpleSinkTask.taskCount.get >= sinkTasks, 10 second)

        // check the data sent by source task
        testUtil.await(() => SimpleSourceTask.taskValues.size == sourceTasks * SimpleSourceTask.dataSet.size, 30 second)
        SimpleSourceTask.dataSet.foreach(value => SimpleSourceTask.taskValues.contains(value) shouldBe true)

        // check the data received by sink task
        testUtil.await(() => SimpleSinkTask.taskValues.size == sourceTasks * SimpleSourceTask.dataSet.size, 30 second)
        SimpleSourceTask.dataSet.foreach(value => SimpleSinkTask.taskValues.contains(value) shouldBe true)
      }
    }
  }

  @Test
  def testHDFSDataNodeWithMultiDataNodes(): Unit = {
    doClose(new OharaTestUtil(3, 2, 1)) { testUtil =>
      {
        val fileSystem: FileSystem = testUtil.hdfsFileSystem()
        val hdfsTempDir: String = testUtil.hdfsTempDir()
        val tmpFolder: Path = new Path(s"$hdfsTempDir/tmp")
        val tmpFile1: Path = new Path(s"$tmpFolder/tempfile1.txt")
        val tmpFile2: Path = new Path(s"$tmpFolder/tempfile2.txt")
        val text: String = "helloworld"
        var helloBytes: Array[Byte] = text.getBytes()

        //Test create folder to local HDFS
        fileSystem.mkdirs(tmpFolder)
        fileSystem.exists(tmpFolder) shouldBe true

        //Test delete folder to local HDFS
        fileSystem.delete(tmpFolder, true)
        fileSystem.exists(tmpFolder) shouldBe false

        //Test create new file to local HDFS
        fileSystem.exists(tmpFile1) shouldBe false
        fileSystem.createNewFile(tmpFile1)
        fileSystem.exists(tmpFile1) shouldBe true

        //Test write data to local HDFS
        val outputStream: OutputStream = fileSystem.create(tmpFile2, true)
        outputStream.write(helloBytes)
        outputStream.close()
        fileSystem.exists(tmpFile2) shouldBe true

        //Test read data from local HDFS
        var inputStream: DataInputStream = fileSystem.open(tmpFile2)
        var buffer: Array[Byte] = Array[Byte]()
        var result: StringBuilder = new StringBuilder()
        Stream
          .continually(inputStream.read())
          .takeWhile(_ != -1)
          .foreach(x => {
            result.append(x.toChar)
          })
        inputStream.close()
        result.toString() shouldBe text

      }
    }
  }
}
