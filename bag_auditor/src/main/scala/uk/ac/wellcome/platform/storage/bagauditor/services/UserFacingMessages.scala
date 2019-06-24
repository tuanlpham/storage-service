package uk.ac.wellcome.platform.storage.bagauditor.services

import grizzled.slf4j.Logging
import uk.ac.wellcome.platform.archive.common.ingests.models.IngestID
import uk.ac.wellcome.platform.archive.common.versioning.{ExternalIdentifiersMismatch, NewerIngestAlreadyExists}
import uk.ac.wellcome.platform.storage.bagauditor.models._

object UserFacingMessages extends Logging {
  def createMessage(ingestId: IngestID, auditError: AuditError): Option[String] =
    auditError match {
      case CannotFindExternalIdentifier(err) =>
        info(
          s"Unable to find an external identifier for $ingestId. Error: $err")
        Some("An external identifier was not found in the bag info")

      case IngestTypeUpdateForNewBag() =>
        Some(
          "Cannot update existing bag: a bag with the supplied external identifier does not exist in this space")

      case IngestTypeCreateForExistingBag() =>
        Some(
          "Cannot create new bag: a bag with the supplied external identifier already exists in this space")

      case UnableToAssignVersion(e: NewerIngestAlreadyExists) =>
        Some(
          s"Another version of this bag was ingested at ${e.stored}, which is newer than the current ingest ${e.request}")

      // This should be impossible, and it strongly points to an error somewhere in
      // the pipeline -- an ingest ID should be used once, and the underlying bag
      // shouldn't change!  We don't bubble up an error because it's an internal failure,
      // and there's nothing the user can do about it.
      case UnableToAssignVersion(e: ExternalIdentifiersMismatch) =>
        warn(s"External identifiers mismatch for $ingestId: $e")
        None

      case _ => None
    }
}
