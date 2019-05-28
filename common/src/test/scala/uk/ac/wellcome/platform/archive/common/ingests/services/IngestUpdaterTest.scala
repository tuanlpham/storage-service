package uk.ac.wellcome.platform.archive.common.ingests.services

import org.scalatest.{FunSpec, TryValues}
import uk.ac.wellcome.platform.archive.common.fixtures.OperationFixtures
import uk.ac.wellcome.platform.archive.common.generators.{BagIdGenerators, IngestOperationGenerators}
import uk.ac.wellcome.platform.archive.common.ingests.fixtures.IngestUpdateAssertions
import uk.ac.wellcome.platform.archive.common.ingests.models.Ingest
import uk.ac.wellcome.platform.archive.common.storage.models.IngestStepStarted

import scala.util.Success

class IngestUpdaterTest
  extends FunSpec
    with IngestUpdateAssertions
    with OperationFixtures
    with IngestOperationGenerators
    with TryValues
    with BagIdGenerators {

  val stepName: String = randomAlphanumeric()

  it("sends an ingest update when successful") {
    val messageSender = createMessageSender
    withIngestUpdater(stepName, messageSender) { ingestUpdater =>
      val ingestId = createIngestID
      val summary = createTestSummary()

      val update = ingestUpdater.send(ingestId, createOperationSuccessWith(summary))

      update shouldBe a[Success[_]]

      assertTopicReceivesIngestEvent(ingestId, messageSender) { events =>
        events should have size 1
        events.head.description shouldBe s"${stepName.capitalize} succeeded"
      }
    }
  }


  it("sends an ingest update when completed") {
    val messageSender = createMessageSender
    withIngestUpdater(stepName, messageSender) { ingestUpdater =>
      val ingestId = createIngestID
      val summary = createTestSummary()

      val bagId = createBagId
      val update = ingestUpdater.send(
        ingestId = ingestId,
        step = createOperationCompletedWith(summary),
        bagId = Some(bagId)
      )

      update shouldBe a[Success[_]]

      assertTopicReceivesIngestStatus(
        ingestId,
        messageSender,
        Ingest.Completed,
        Some(bagId)) { events =>
        events should have size 1
        events.head.description shouldBe s"${stepName.capitalize} succeeded (completed)"
      }
    }
  }

  it("sends an ingest update when failed") {
    val messageSender = createMessageSender
    withIngestUpdater(stepName, messageSender) { ingestUpdater =>
      val ingestId = createIngestID
      val summary = createTestSummary()

      val bagId = createBagId
      val update = ingestUpdater.send(
        ingestId = ingestId,
        step = createIngestFailureWith(summary),
        bagId = Some(bagId)
      )

      update shouldBe a[Success[_]]

      assertTopicReceivesIngestStatus(
        ingestId,
        messageSender,
        Ingest.Failed,
        Some(bagId)) { events =>
        events should have size 1
        events.head.description shouldBe s"${stepName.capitalize} failed"
      }
    }
  }

  it("sends an ingest update when an ingest step starts") {
    val messageSender = createMessageSender
    withIngestUpdater(stepName, messageSender) { ingestUpdater =>
      val ingestId = createIngestID

      val update = ingestUpdater.send(
        ingestId = ingestId,
        step = IngestStepStarted(ingestId)
      )

      update shouldBe a[Success[_]]

      assertTopicReceivesIngestEvent(ingestId, messageSender) { events =>
        events should have size 1
        events.head.description shouldBe s"${stepName.capitalize} started"
      }
    }
  }

  it("sends an ingest update when failed with a failure message") {
    val messageSender = createMessageSender
    withIngestUpdater(stepName, messageSender) { ingestUpdater =>
      val ingestId = createIngestID
      val summary = createTestSummary()
      val failureMessage = randomAlphanumeric(length = 50)

      val bagId = createBagId

      val update = ingestUpdater.send(
        ingestId = ingestId,
        step = createIngestFailureWith(
          summary,
          maybeFailureMessage = Some(failureMessage)
        ),
        bagId = Some(bagId)
      )

      update shouldBe a[Success[_]]

      assertTopicReceivesIngestStatus(
        ingestId,
        messageSender,
        Ingest.Failed,
        Some(bagId)) { events =>
        events should have size 1
        events.head.description shouldBe s"${stepName.capitalize} failed - $failureMessage"
      }
    }
  }
}
