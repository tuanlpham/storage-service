package uk.ac.wellcome.platform.archive.bagverifier

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.platform.archive.bagverifier.fixtures.WorkerServiceFixture
import uk.ac.wellcome.platform.archive.common.fixtures.BagLocationFixtures
import uk.ac.wellcome.platform.archive.common.generators.BagRequestGenerators
import uk.ac.wellcome.platform.archive.common.ingests.models.Ingest
import uk.ac.wellcome.platform.archive.common.ingest.IngestUpdateAssertions

class VerifierFeatureTest
    extends FunSpec
    with Matchers
    with ScalaFutures
    with BagLocationFixtures
    with BagRequestGenerators
    with IntegrationPatience
    with IngestUpdateAssertions
    with WorkerServiceFixture {

  it(
    "updates the ingest monitor and sends an outgoing notification if verification succeeds") {
    withLocalSnsTopic { ingestTopic =>
      withLocalSnsTopic { outgoingTopic =>
        withLocalSqsQueueAndDlq {
          case QueuePair(queue, dlq) =>
            withWorkerService(ingestTopic, outgoingTopic, queue) { _ =>
              withLocalS3Bucket { bucket =>
                withBag(bucket) { bagLocation =>
                  val bagRequest = createBagRequestWith(bagLocation)

                  sendNotificationToSQS(queue, bagRequest)

                  eventually {
                    listMessagesReceivedFromSNS(outgoingTopic)

                    topicReceivesIngestEvent(
                      requestId = bagRequest.requestId,
                      ingestTopic = ingestTopic
                    ) { events =>
                      events.map {
                        _.description
                      } shouldBe List("Verification succeeded")
                    }

                    assertSnsReceivesOnly(bagRequest, topic = outgoingTopic)

                    assertQueueEmpty(queue)
                    assertQueueEmpty(dlq)
                  }
                }
              }
            }
        }
      }
    }
  }

  it(
    "deletes the SQS message if the bag can be verified but has incorrect checksums") {
    withLocalSnsTopic { ingestTopic =>
      withLocalSnsTopic { outgoingTopic =>
        withLocalSqsQueueAndDlq {
          case QueuePair(queue, dlq) =>
            withWorkerService(ingestTopic, outgoingTopic, queue) { _ =>
              withLocalS3Bucket { bucket =>
                withBag(
                  bucket,
                  createDataManifest = dataManifestWithWrongChecksum) {
                  bagLocation =>
                    val bagRequest = createBagRequestWith(bagLocation)

                    sendNotificationToSQS(queue, bagRequest)

                    eventually {
                      topicReceivesIngestStatus(
                        requestId = bagRequest.requestId,
                        ingestTopic = ingestTopic,
                        status = Ingest.Failed
                      ) { events =>
                        val description = events.map {
                          _.description
                        }.head
                        description should startWith("Verification failed")
                      }

                      assertSnsReceivesNothing(outgoingTopic)

                      assertQueueEmpty(queue)
                      assertQueueEmpty(dlq)
                    }
                }
              }
            }
        }
      }
    }
  }
}