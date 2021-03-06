package uk.ac.wellcome.platform.storage.bag_versioner.versioning

import java.time.Instant

import uk.ac.wellcome.platform.archive.common.bagit.models.{
  BagVersion,
  ExternalIdentifier
}
import uk.ac.wellcome.platform.archive.common.ingests.models.IngestID
import uk.ac.wellcome.platform.archive.common.storage.models.StorageSpace

case class VersionRecord(
  externalIdentifier: ExternalIdentifier,
  ingestId: IngestID,
  ingestDate: Instant,
  storageSpace: StorageSpace,
  version: BagVersion
)
