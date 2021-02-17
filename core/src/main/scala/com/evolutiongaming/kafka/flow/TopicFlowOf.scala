package com.evolutiongaming.kafka.flow

import cats.Parallel
import cats.effect.{Concurrent, Resource}
import com.evolutiongaming.catshelper.LogOf
import com.evolutiongaming.skafka.Topic
import com.evolutiongaming.skafka.consumer.Consumer
import scodec.bits.ByteVector

trait TopicFlowOf[F[_]] {

  def apply(consumer: Consumer[F, String, ByteVector], topic: Topic): Resource[F, TopicFlow[F]]

}
object TopicFlowOf {

  def apply[F[_]: Concurrent: Parallel: LogOf](
    partitionFlowOf: PartitionFlowOf[F]
  ): TopicFlowOf[F] = { (consumer, topic) =>
    TopicFlow.of(consumer, topic, partitionFlowOf)
  }

  def route[F[_]](f: Topic => TopicFlowOf[F]): TopicFlowOf[F] = { (consumer, topic) =>
    val flowOf = f(topic)
    flowOf(consumer, topic)
  }

}
