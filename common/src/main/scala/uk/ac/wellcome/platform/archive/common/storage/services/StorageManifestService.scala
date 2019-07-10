package uk.ac.wellcome.platform.archive.common.storage.services

import java.time.Instant

import grizzled.slf4j.Logging
import uk.ac.wellcome.platform.archive.common.bagit.models.{Bag, BagManifest, BagPath}
import uk.ac.wellcome.platform.archive.common.bagit.services.BagMatcher
import uk.ac.wellcome.platform.archive.common.storage.models.{FileManifest, StorageManifest, StorageManifestFile, StorageSpace}
import uk.ac.wellcome.storage.{ObjectLocation, ObjectLocationPrefix}

import scala.util.{Failure, Success, Try}

class StorageManifestException(message: String) extends RuntimeException(message)

object StorageManifestService extends Logging {
  def createManifest(
    bag: Bag,
    replicaRootLocation: ObjectLocation,
    version: Int
  ): Try[StorageManifest] = {
    for {
      bagRoot <- getBagRoot(replicaRootLocation, version)

      entries <- createNamePathMap(bag, bagRoot = bagRoot, version = version)

      fileManifestFiles <- createManifestFiles(
        manifest = bag.manifest,
        entries = entries
      )

      tagManifestFiles <- createManifestFiles(
        manifest = bag.tagManifest,
        entries = entries
      )

      storageManifest = StorageManifest(
        space = StorageSpace("123"),
        info = bag.info,
        version = version,
        manifest = FileManifest(
          checksumAlgorithm = bag.manifest.checksumAlgorithm,
          files = fileManifestFiles
        ),
        tagManifest = FileManifest(
          checksumAlgorithm = bag.tagManifest.checksumAlgorithm,
          files = tagManifestFiles
        ),
        locations = List.empty,
        createdDate = Instant.now
      )
    } yield storageManifest
  }

  /** The replicator writes bags inside a bucket to paths of the form
    *
    *     /{storageSpace}/{externalIdentifier}/v{version}
    *
    * All the versions of a bag should follow this convention, so if we
    * strip off the /:version prefix we'll find the root of all bags
    * with this (storage space, external ID) pair.
    *
    * TODO: It would be better if we passed a structured object out of
    * the replicator.
    *
    */
  private def getBagRoot(replicaRootLocation: ObjectLocation, version: Int): Try[ObjectLocationPrefix] =
    if (replicaRootLocation.path.endsWith(s"/v$version")) {
      Success(
        replicaRootLocation.asPrefix.copy(
          path = replicaRootLocation.path.stripSuffix(s"/v$version")
        )
      )
    } else {
      Failure(new StorageManifestException(s"Malformed bag root: $replicaRootLocation (expected suffix /v$version)"))
    }

  /** Every entry in the bag manifest will be either a:
    *
    *   - concrete file inside the replicated bag, or
    *   - a file referenced by the fetch file, which should be in a different
    *     versioned directory under the same bag root
    *
    * This function gets a map (bag name) -> (path), relative to the bag root.
    *
    */
  private def createNamePathMap(
    bag: Bag,
    bagRoot: ObjectLocationPrefix,
    version: Int): Try[Map[BagPath, String]] = Try {
    BagMatcher.correlateFetchEntries(bag) match {
      case Right(matchedLocations) =>
        matchedLocations.map { matchedLoc =>
          val path = matchedLoc.fetchEntry match {
            case None             => matchedLoc.bagFile.path.value
            case Some(fetchEntry) => fetchEntry.uri.getPath
          }

          (matchedLoc.bagFile.path, path)
        }.toMap

      case Left(err) =>
        throw new StorageManifestException(
          s"Unable to resolve fetch entries: $err"
        )
    }
  }

  private def createManifestFiles(manifest: BagManifest, entries: Map[BagPath, String]) = Try {
    manifest.files.map { bagFile =>
      // This lookup should never file -- the BagMatcher populates the
      // entries from the original manifests in the bag.
      //
      // We wrap it in a Try block just in case, but this should never
      // throw in practice.
      val path = entries(bagFile.path)

      StorageManifestFile(
        checksum = bagFile.checksum,
        name = bagFile.path.value,
        path = path
      )
    }
  }
}
