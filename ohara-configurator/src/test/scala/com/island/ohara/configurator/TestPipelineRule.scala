package com.island.ohara.configurator

import com.island.ohara.client.ConfiguratorJson.{
  Pipeline,
  PipelineRequest,
  Sink,
  SinkRequest,
  Source,
  SourceRequest,
  TopicInfo,
  TopicInfoRequest
}
import com.island.ohara.client.{ConfiguratorClient, ConfiguratorJson}
import com.island.ohara.common.data.connector.ConnectorState
import com.island.ohara.common.rule.SmallTest
import com.island.ohara.common.util.ReleaseOnce
import org.junit.{After, Test}
import org.scalatest.Matchers

class TestPipelineRule extends SmallTest with Matchers {

  private[this] val configurator = Configurator.local()

  private[this] val client = ConfiguratorClient(configurator.hostname, configurator.port)

  @Test
  def testPipelineStateAfterStartingSource(): Unit = {
    val topic = client.add[TopicInfoRequest, TopicInfo](TopicInfoRequest(methodName, 1, 1))
    val sourceRequest = SourceRequest(
      name = "abc",
      className = "jdbc",
      schema = Seq.empty,
      topics = Seq(topic.id),
      configs = Map.empty,
      numberOfTasks = 1
    )

    val source = client.add[SourceRequest, Source](sourceRequest)
    val pipeline = client.add[PipelineRequest, Pipeline](
      PipelineRequest(
        name = "abc",
        rules = Map(source.id -> ConfiguratorJson.UNKNOWN)
      ))
    pipeline.objects.foreach(obj => obj.state shouldBe None)

    // start source and pipeline should "see" what happen in source
    client.start[Source](source.id)
    val pipeline2 = client.get[Pipeline](pipeline.id)
    pipeline2.objects.foreach(obj => obj.state.get shouldBe ConnectorState.RUNNING)
  }

  @Test
  def testUnknownObject(): Unit = {
    val sourceRequest = SourceRequest(
      name = "abc",
      className = "jdbc",
      schema = Seq.empty,
      topics = Seq.empty,
      configs = Map.empty,
      numberOfTasks = 1
    )

    val source = client.add[SourceRequest, Source](sourceRequest)

    client.add[PipelineRequest, Pipeline](
      PipelineRequest(
        name = "abc",
        rules = Map(source.id -> ConfiguratorJson.UNKNOWN)
      ))

    val sinkRequest = SinkRequest(
      name = "abc",
      className = "jdbc",
      schema = Seq.empty,
      topics = Seq.empty,
      configs = Map.empty,
      numberOfTasks = 1
    )

    val sink = client.add[SinkRequest, Sink](sinkRequest)

    client.add[PipelineRequest, Pipeline](
      PipelineRequest(
        name = "abc",
        rules = Map(ConfiguratorJson.UNKNOWN -> sink.id)
      ))
  }

  @After
  def tearDown(): Unit = {
    ReleaseOnce.close(client)
    ReleaseOnce.close(configurator)
  }
}
