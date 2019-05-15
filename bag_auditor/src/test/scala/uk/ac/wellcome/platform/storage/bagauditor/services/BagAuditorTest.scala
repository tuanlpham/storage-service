package uk.ac.wellcome.platform.storage.bagauditor.services

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSpec, Matchers, TryValues}
import uk.ac.wellcome.platform.archive.common.fixtures.BagLocationFixtures
import uk.ac.wellcome.platform.archive.common.storage.models.IngestFailed
import uk.ac.wellcome.platform.storage.bagauditor.models.{AuditFailureSummary, AuditSuccessSummary}

class BagAuditorTest
  extends FunSpec
    with Matchers
    with ScalaFutures
    with TryValues
    with BagLocationFixtures {
  val bagAuditor = new BagAuditor()

  it("gets the audit information for a valid bag") {
    withLocalS3Bucket { bucket =>
      val bagInfo = createBagInfo
      withBag(bucket, bagInfo = bagInfo) {
        case (root, space) =>

          val maybeAudit = bagAuditor.getAuditSummary(
            location = root,
            space = space
          )

          val result = maybeAudit.success.get
          val summary = result.summary
            .asInstanceOf[AuditSuccessSummary]

          summary.audit.root shouldBe root
          summary.audit.externalId shouldBe bagInfo.externalIdentifier
          summary.audit.version shouldBe 1

      }
    }
  }

  it("errors if it cannot find the bag root") {

    withLocalS3Bucket { bucket =>
      withBag(bucket, bagRootDirectory = Some("1/2/3")) {
        case (_, storageSpace) =>

          val maybeAudit = bagAuditor.getAuditSummary(
            location = createObjectLocationWith(bucket, key = "1/"),
            space = storageSpace
          )

          val result = maybeAudit.success.get

          result shouldBe a[IngestFailed[_]]
          result.summary shouldBe a[AuditFailureSummary]
      }
    }
  }

  it("errors if it cannot find the bag identifier") {
    withLocalS3Bucket { bucket =>
      withBag(bucket) {
        case (bagRootLocation, storageSpace) =>
          val bagInfoLocation = bagRootLocation.join("bag-info.txt")
          s3Client.deleteObject(
            bagInfoLocation.namespace,
            bagInfoLocation.key
          )

          val maybeAudit = bagAuditor.getAuditSummary(
            location = bagRootLocation,
            space = storageSpace
          )

          val result = maybeAudit.success.get

          result shouldBe a[IngestFailed[_]]
          result.summary shouldBe a[AuditFailureSummary]
      }
    }
  }
}
