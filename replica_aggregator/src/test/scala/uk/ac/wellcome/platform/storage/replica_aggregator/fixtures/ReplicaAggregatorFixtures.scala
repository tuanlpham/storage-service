package uk.ac.wellcome.platform.storage.replica_aggregator.fixtures

import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.fixtures.worker.AlpakkaSQSWorkerFixtures
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.platform.archive.common.fixtures.{
  MonitoringClientFixture,
  OperationFixtures
}
import uk.ac.wellcome.platform.storage.replica_aggregator.models.{
  AggregatorInternalRecord,
  ReplicaPath
}
import uk.ac.wellcome.platform.storage.replica_aggregator.services.{
  ReplicaAggregator,
  ReplicaAggregatorWorker,
  ReplicaCounter
}
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore

trait ReplicaAggregatorFixtures
    extends OperationFixtures
    with Akka
    with AlpakkaSQSWorkerFixtures
    with MonitoringClientFixture {

  private val defaultQueue = Queue(
    url = "default_q",
    arn = "arn::default_q"
  )

  def withReplicaAggregatorWorker[R](
    queue: Queue = defaultQueue,
    versionedStore: VersionedStore[ReplicaPath, Int, AggregatorInternalRecord] =
      MemoryVersionedStore[ReplicaPath, AggregatorInternalRecord](
        initialEntries = Map.empty
      ),
    ingests: MemoryMessageSender,
    outgoing: MemoryMessageSender,
    stepName: String = randomAlphanumericWithLength(),
    expectedReplicaCount: Int = 1
  )(testWith: TestWith[ReplicaAggregatorWorker[String, String], R]): R =
    withActorSystem { implicit actorSystem =>
      val ingestUpdater = createIngestUpdaterWith(ingests, stepName = stepName)
      val outgoingPublisher = createOutgoingPublisherWith(outgoing)

      withMonitoringClient { implicit monitoringClient =>
        val worker = new ReplicaAggregatorWorker(
          config = createAlpakkaSQSWorkerConfig(queue),
          replicaAggregator = new ReplicaAggregator(versionedStore),
          replicaCounter =
            new ReplicaCounter(expectedReplicaCount = expectedReplicaCount),
          ingestUpdater = ingestUpdater,
          outgoingPublisher = outgoingPublisher
        )

        worker.run()

        testWith(worker)
      }
    }
}
