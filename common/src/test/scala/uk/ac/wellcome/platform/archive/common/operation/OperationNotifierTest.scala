package uk.ac.wellcome.platform.archive.common.operation

import java.util.UUID

import org.scalatest.FunSpec
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.archive.common.fixtures.{
  OperationFixtures,
  RandomThings
}
import uk.ac.wellcome.platform.archive.common.ingest.IngestUpdateAssertions
import uk.ac.wellcome.platform.archive.common.ingests.models.Ingest
import uk.ac.wellcome.platform.archive.common.ingests.operation.{
  OperationCompleted,
  OperationFailure,
  OperationSuccess
}

import scala.concurrent.ExecutionContext.Implicits.global

class OperationNotifierTest
    extends FunSpec
    with RandomThings
    with ScalaFutures
    with IngestUpdateAssertions
    with Eventually
    with IntegrationPatience
    with OperationFixtures {

  case class TestSummary(
    description: String
  ) {
    override def toString: String = this.description
  }

  describe("with a failed operation") {
    it("only sends a failed ingest update") {
      withLocalSnsTopic { ingestTopic =>
        withLocalSnsTopic { outgoingTopic =>
          val requestId = UUID.randomUUID()

          val operationName = randomAlphanumeric()

          withOperationNotifier(
            operationName,
            ingestTopic = ingestTopic,
            outgoingTopic = outgoingTopic
          ) { operationNotifier =>
            val summary = TestSummary(
              randomAlphanumeric()
            )

            val throwable =
              new RuntimeException(
                randomAlphanumeric()
              )

            val operation = OperationFailure(
              summary,
              throwable
            )

            val sendingOperationNotice =
              operationNotifier
                .send(requestId, operation)(identity)

            whenReady(sendingOperationNotice) { _ =>
              eventually {

                topicReceivesIngestStatus(
                  requestId = requestId,
                  ingestTopic = ingestTopic,
                  status = Ingest.Failed
                ) { events =>
                  val description = events.map {
                    _.description
                  }.head

                  description should startWith(
                    s"${operationName.capitalize} failed"
                  )
                }

                assertSnsReceivesNothing(outgoingTopic)
              }
            }
          }
        }
      }
    }
  }

  describe("with a successful operation") {
    it("sends an event ingest update and an outgoing message") {
      withLocalSnsTopic { ingestTopic =>
        withLocalSnsTopic { outgoingTopic =>
          val requestId = UUID.randomUUID()

          val operationName = randomAlphanumeric()
          withOperationNotifier(
            operationName,
            ingestTopic = ingestTopic,
            outgoingTopic = outgoingTopic
          ) { operationNotifier =>
            val summary = TestSummary(
              randomAlphanumeric()
            )

            val operation = OperationSuccess(
              summary
            )

            val sendingOperationNotice =
              operationNotifier
                .send(requestId, operation)(identity)

            whenReady(sendingOperationNotice) { _ =>
              eventually {

                topicReceivesIngestEvent(requestId, ingestTopic) { events =>
                  events should have size 1
                  events.head.description shouldBe s"${operationName.capitalize} succeeded"
                }

                assertSnsReceivesOnly(summary, outgoingTopic)
              }
            }
          }
        }
      }
    }
  }

  describe("with a completed operation") {
    it("sends a completed ingest update and an outgoing message") {
      withLocalSnsTopic { ingestTopic =>
        withLocalSnsTopic { outgoingTopic =>
          val requestId = UUID.randomUUID()

          val operationName = randomAlphanumeric()
          withOperationNotifier(
            operationName,
            ingestTopic = ingestTopic,
            outgoingTopic = outgoingTopic
          ) { operationNotifier =>
            val summary = TestSummary(
              randomAlphanumeric()
            )

            val operation = OperationCompleted(
              summary
            )

            val sendingOperationNotice =
              operationNotifier
                .send(requestId, operation)(identity)

            whenReady(sendingOperationNotice) { _ =>
              eventually {
                topicReceivesIngestStatus(
                  requestId = requestId,
                  ingestTopic = ingestTopic,
                  status = Ingest.Completed
                ) { events =>
                  val description = events.map {
                    _.description
                  }.head

                  description should startWith(
                    s"${operationName.capitalize} succeeded (completed)"
                  )
                }

                assertSnsReceivesOnly(summary, outgoingTopic)
              }
            }
          }
        }
      }
    }
  }
}