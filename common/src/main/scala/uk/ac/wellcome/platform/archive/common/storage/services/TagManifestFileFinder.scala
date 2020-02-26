package uk.ac.wellcome.platform.archive.common.storage.services

import java.io.InputStream

import uk.ac.wellcome.platform.archive.common.bagit.models.UnreferencedFiles
import uk.ac.wellcome.platform.archive.common.storage.models.StorageManifestFile
import uk.ac.wellcome.platform.archive.common.verify.{
  ChecksumValue,
  Hasher,
  HashingAlgorithm
}
import uk.ac.wellcome.storage.store.Readable
import uk.ac.wellcome.storage.streaming.HasLength
import uk.ac.wellcome.storage.{
  DoesNotExistError,
  ObjectLocation,
  ObjectLocationPrefix
}

import scala.util.Try

/** The tag manifest files (e.g. tagmanifest-sha256.txt) aren't referred to by
  * any of the other manifests in the bag, but we still want to include them in
  * the storage manifest created by the storage service.
  *
  * This class creates the `StorageManifestFile` entries for BagIt tag manifest files.
  *
  */
class TagManifestFileFinder[IS <: InputStream with HasLength](
  implicit streamReader: Readable[ObjectLocation, IS]
) {

  def getTagManifestFiles(
    prefix: ObjectLocationPrefix,
    algorithm: HashingAlgorithm
  ): Try[Seq[StorageManifestFile]] = Try {
    val entries: Seq[StorageManifestFile] =
      UnreferencedFiles.tagManifestFiles.flatMap {
        findIndividualTagManifestFile(_, prefix, algorithm)
      }

    if (entries.isEmpty) {
      throw new RuntimeException(s"No tag manifest files found under $prefix")
    } else {
      entries
    }
  }

  private def findIndividualTagManifestFile(
    name: String,
    prefix: ObjectLocationPrefix,
    algorithm: HashingAlgorithm
  ): Option[StorageManifestFile] =
    streamReader.get(prefix.asLocation(name)) match {
      case Right(is) =>
        // TODO: we could handle the checksum more gracefully here, not '.get'
        //
        // This method is called in the bag register to create the storage manifest,
        // so it happens after the entire bag has been verified.  To verify the bag,
        // we've already had to read the tagmanifest-sha256.txt file, so an error
        // here would be unlikely (but probably not impossible).
        val checksum = Hasher.hash(is.identifiedT)
          .get
          .getChecksumValue(algorithm)

        Some(
          StorageManifestFile(
            checksum = checksum,
            name = name,
            path = name,
            size = is.identifiedT.length
          )
        )

      case Left(err: DoesNotExistError) => None

      case Left(err) =>
        throw new RuntimeException(s"Error looking up $prefix/$name: $err")
    }
}
