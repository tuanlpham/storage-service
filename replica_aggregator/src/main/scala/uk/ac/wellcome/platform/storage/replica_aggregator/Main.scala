package uk.ac.wellcome.platform.storage.replica_aggregator

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.typesafe.config.Config
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.typesafe.{
  AlpakkaSqsWorkerConfigBuilder,
  CloudwatchMonitoringClientBuilder,
  SQSBuilder
}
import uk.ac.wellcome.messaging.worker.monitoring.CloudwatchMonitoringClient
import uk.ac.wellcome.platform.archive.common.config.builders.{
  IngestUpdaterBuilder,
  OperationNameBuilder,
  OutgoingPublisherBuilder
}
import uk.ac.wellcome.platform.storage.replica_aggregator.services.{
  ReplicaAggregator,
  ReplicaAggregatorWorker
}
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContextExecutor

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem = AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContextExecutor =
      actorSystem.dispatcher
    implicit val mat: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()

    implicit val monitoringClient: CloudwatchMonitoringClient =
      CloudwatchMonitoringClientBuilder.buildCloudwatchMonitoringClient(config)

    implicit val sqsClient: AmazonSQSAsync =
      SQSBuilder.buildSQSAsyncClient(config)

    val operationName = OperationNameBuilder.getName(config)

    // TODO: Needs a versioned store!
    val replicaAggregator = new ReplicaAggregator()

    new ReplicaAggregatorWorker(
      config = AlpakkaSqsWorkerConfigBuilder.build(config),
      replicaAggregator = replicaAggregator,
      ingestUpdater = IngestUpdaterBuilder.build(config, operationName),
      outgoingPublisher = OutgoingPublisherBuilder.build(config, operationName)
    )
  }
}
