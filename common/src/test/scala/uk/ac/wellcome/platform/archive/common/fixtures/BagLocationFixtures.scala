package uk.ac.wellcome.platform.archive.common.fixtures

import java.net.URI

import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.platform.archive.common.bagit.models.{
  BagFetch,
  BagFetchEntry,
  BagInfo,
  BagPath
}
import uk.ac.wellcome.platform.archive.common.generators.{
  BagInfoGenerators,
  StorageSpaceGenerators
}
import uk.ac.wellcome.platform.archive.common.storage.models.StorageSpace
import uk.ac.wellcome.storage.ObjectLocation
import uk.ac.wellcome.storage.fixtures.S3Fixtures
import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket
import uk.ac.wellcome.storage.store.TypedStoreEntry
import uk.ac.wellcome.storage.store.s3.{S3StreamStore, S3TypedStore}

import scala.util.Random

trait BagLocationFixtures
    extends S3Fixtures
    with BagInfoGenerators
    with BagIt
    with StorageSpaceGenerators {

  implicit val s3StreamStore: S3StreamStore = new S3StreamStore()
  implicit val s3TypedStore: S3TypedStore[String] = new S3TypedStore[String]()

  def withBag[R](
    bucket: Bucket,
    bagInfo: BagInfo = createBagInfo,
    dataFileCount: Int = 1,
    storageSpace: StorageSpace = createStorageSpace,
    createDataManifest: List[(String, String)] => Option[FileEntry] =
      createValidDataManifest,
    createTagManifest: List[(String, String)] => Option[FileEntry] =
      createValidTagManifest,
    bagRootDirectory: Option[String] = None)(
    testWith: TestWith[(ObjectLocation, StorageSpace), R]): R = {
    val bagIdentifier = createExternalIdentifier

    info(s"Creating Bag $bagIdentifier")

    val fileEntries = createBag(
      bagInfo,
      dataFileCount = dataFileCount,
      createDataManifest = createDataManifest,
      createTagManifest = createTagManifest)

    debug(s"fileEntries: $fileEntries")

    val storageSpaceRootLocation = createObjectLocationWith(
      bucket = bucket,
      key = storageSpace.toString
    )

    val bagRootLocation = storageSpaceRootLocation.join(
      bagIdentifier.toString
    )

    val unpackedBagLocation = bagRootLocation.join(
      bagRootDirectory.getOrElse("")
    )

    // To simulate a bag that contains both concrete objects and
    // fetch files, siphon off some of the entries to be written
    // as a fetch file.
    //
    // We need to make sure bag-info.txt, manifest.txt, and so on,
    // are written into the bag proper.
    val (realFiles, fetchFiles) = fileEntries.partition { file =>
      file.name.endsWith(".txt") || Random.nextFloat() < 0.8
    }

    realFiles.map { entry =>
      val entryLocation = unpackedBagLocation.join(entry.name)
      s3Client
        .putObject(
          entryLocation.namespace,
          entryLocation.path,
          entry.contents
        )
    }

    val bagFetchEntries = fetchFiles.map { entry =>
      val entryLocation = createObjectLocationWith(bucket)
      s3TypedStore.put(entryLocation)(TypedStoreEntry(
        entry.contents,
        metadata = Map.empty)) shouldBe a[Right[_, _]]

      BagFetchEntry(
        uri = new URI(s"s3://${entryLocation.namespace}/${entryLocation.path}"),
        length = Some(entry.contents.length),
        path = BagPath(entry.name)
      )
    }

    if (fetchFiles.nonEmpty) {
      val fetchLocation = unpackedBagLocation.join("fetch.txt")
      val fetchContents = BagFetch.write(bagFetchEntries)

      s3TypedStore.put(fetchLocation)(TypedStoreEntry(
        fetchContents,
        metadata = Map.empty)) shouldBe a[Right[_, _]]
    }

    testWith((bagRootLocation, storageSpace))
  }
}
