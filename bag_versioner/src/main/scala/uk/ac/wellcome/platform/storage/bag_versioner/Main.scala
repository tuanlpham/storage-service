package uk.ac.wellcome.platform.storage.bag_versioner

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.Id
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.typesafe.config.Config
import uk.ac.wellcome.json.JsonUtil._
import org.scanamo.auto._
import org.scanamo.time.JavaTimeFormats._
import uk.ac.wellcome.messaging.typesafe.{
  AlpakkaSqsWorkerConfigBuilder,
  CloudwatchMonitoringClientBuilder,
  SQSBuilder
}
import uk.ac.wellcome.messaging.worker.monitoring.CloudwatchMonitoringClient
import uk.ac.wellcome.platform.archive.common.bagit.models.BagVersion
import uk.ac.wellcome.platform.archive.common.config.builders.{
  IngestUpdaterBuilder,
  OperationNameBuilder,
  OutgoingPublisherBuilder
}
import uk.ac.wellcome.platform.storage.bag_versioner.services.{
  BagVersioner,
  BagVersionerWorker
}
import uk.ac.wellcome.platform.storage.bag_versioner.versioning.dynamo.{
  DynamoIngestVersionManager,
  DynamoIngestVersionManagerDao
}
import uk.ac.wellcome.platform.storage.bag_versioner.versioning.{
  IngestVersionManagerError,
  VersionPicker
}
import uk.ac.wellcome.storage.locking.dynamo.DynamoLockingService
import uk.ac.wellcome.storage.typesafe.{DynamoBuilder, DynamoLockDaoBuilder}
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

    implicit val lockDao = DynamoLockDaoBuilder
      .buildDynamoLockDao(config)

    val operationName = OperationNameBuilder.getName(config)

    val lockingService =
      new DynamoLockingService[
        Either[IngestVersionManagerError, BagVersion],
        Id
      ]()

    val ingestVersionManagerDao = new DynamoIngestVersionManagerDao(
      dynamoClient = DynamoBuilder.buildDynamoClient(config),
      dynamoConfig =
        DynamoBuilder.buildDynamoConfig(config, namespace = "versions")
    )

    val versionPicker = new VersionPicker(
      lockingService = lockingService,
      ingestVersionManager =
        new DynamoIngestVersionManager(ingestVersionManagerDao)
    )

    new BagVersionerWorker(
      config = AlpakkaSqsWorkerConfigBuilder.build(config),
      bagVersioner = new BagVersioner(versionPicker = versionPicker),
      ingestUpdater = IngestUpdaterBuilder.build(config, operationName),
      outgoingPublisher = OutgoingPublisherBuilder.build(config, operationName)
    )
  }
}
