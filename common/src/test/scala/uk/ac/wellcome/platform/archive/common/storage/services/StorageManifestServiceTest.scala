package uk.ac.wellcome.platform.archive.common.storage.services

import java.net.URI

import org.scalatest.{Assertion, FunSpec, Matchers, TryValues}
import uk.ac.wellcome.platform.archive.common.bagit.models.{Bag, BagFetchEntry, BagFile, BagPath}
import uk.ac.wellcome.platform.archive.common.generators.BagGenerators
import uk.ac.wellcome.platform.archive.common.ingests.models.{InfrequentAccessStorageProvider, StorageLocation}
import uk.ac.wellcome.platform.archive.common.storage.models.StorageManifest
import uk.ac.wellcome.platform.archive.common.verify.{Checksum, ChecksumValue, MD5, SHA256}
import uk.ac.wellcome.storage.ObjectLocation
import uk.ac.wellcome.storage.generators.ObjectLocationGenerators

class StorageManifestServiceTest
  extends FunSpec
    with Matchers
    with BagGenerators
    with ObjectLocationGenerators
    with TryValues {

  it("rejects a bag if the replica root is not a versioned directory") {
    val replicaRootLocation = createObjectLocation
    val version = randomInt(1, 10)

    assertIsError(replicaRootLocation = replicaRootLocation, version = version) {
      _ shouldBe s"Malformed bag root: $replicaRootLocation (expected suffix /v$version)"
    }
  }

  it("rejects a bag if the replica root has the wrong version") {
    val version = randomInt(1, 10)
    val replicaRootLocation = createObjectLocation.join(s"/v${version + 1}")

    assertIsError(replicaRootLocation = replicaRootLocation, version = version) {
      _ shouldBe s"Malformed bag root: $replicaRootLocation (expected suffix /v$version)"
    }
  }

  it("rejects a bag if the fetch.txt refers to files that aren't in the manifest") {
    val fetchEntries = Seq(
      BagFetchEntry(
        uri = new URI("https://example.org/file1.txt"),
        length = None,
        path = BagPath(randomAlphanumeric)
      )
    )

    val bag = createBagWith(
      fetchEntries = fetchEntries
    )

    assertIsError(bag = bag) { msg =>
      msg should startWith("Unable to resolve fetch entries:")
      msg should include(s"Fetch entry refers to a path that isn't in the bag: ${fetchEntries.head}")
    }
  }

  it("identifies the correct root location of a bag") {
    val version = randomInt(1, 10)
    val bagRoot = createObjectLocation
    val replicaRoot = bagRoot.join(s"/v$version")

    val storageManifest = createManifest(
      replicaRoot = replicaRoot,
      version = version
    )

    storageManifest.locations shouldBe Seq(
      StorageLocation(
        provider = InfrequentAccessStorageProvider,
        location = bagRoot
      )
    )
  }

  it("if there are no fetch entries, it puts all the file entries under a versioned path") {
    val version = randomInt(1, 10)
    val replicaRoot = createObjectLocation.join(s"/v$version")

    val files = Seq("data/file1.txt", "data/file2.txt", "data/dir/file3.txt")

    val bag = createBagWith(
      manifestFiles = files.map { f =>
        BagFile(
          checksum = Checksum(SHA256, ChecksumValue(randomAlphanumeric)),
          path = BagPath(f)
        )
      }
    )

    val storageManifest = createManifest(
      bag = bag,
      replicaRoot = replicaRoot,
      version = version
    )

    val namePathMap =
      storageManifest.manifest.files
        .map { file => (file.name, file.path) }
        .toMap

    namePathMap shouldBe files.map { f => (f, s"v$version/$f") }.toMap
  }

  it("if there are no fetch entries, it puts all the tag manifest under a versioned path") {
    val version = randomInt(1, 10)
    val replicaRoot = createObjectLocation.join(s"/v$version")

    val files = Seq("bag-info.txt", "tag-manifest-sha256.txt", "manifest-sha256.txt")

    val bag = createBagWith(
      tagManifestFiles = files.map { f =>
        BagFile(
          checksum = Checksum(SHA256, ChecksumValue(randomAlphanumeric)),
          path = BagPath(f)
        )
      }
    )

    val storageManifest = createManifest(
      bag = bag,
      replicaRoot = replicaRoot,
      version = version
    )

    val namePathMap =
      storageManifest.tagManifest.files
        .map { file => (file.name, file.path) }
        .toMap

    namePathMap shouldBe files.map { f => (f, s"v$version/$f") }.toMap
  }

  it("uses the checksums from the file manifest") {
    val filesWithChecksums =
      Seq("data/file1.txt", "data/file2.txt", "data/dir/file3.txt")
        .map { _ -> ChecksumValue(randomAlphanumeric) }

    val bag = createBagWith(
      manifestFiles = filesWithChecksums.map { case (f, checksumValue) =>
        BagFile(
          checksum = Checksum(SHA256, checksumValue),
          path = BagPath(f)
        )
      }
    )

    val storageManifest = createManifest(bag = bag)

    val nameChecksumMap =
      storageManifest.manifest.files
        .map { file => (file.name, file.checksum) }

    nameChecksumMap should contain theSameElementsAs filesWithChecksums
  }

  it("uses the checksums from the tag manifest") {
    val filesWithChecksums =
      Seq("bag-info.txt", "tag-manifest-sha256.txt", "manifest-sha256.txt")
        .map { _ -> ChecksumValue(randomAlphanumeric) }

    val bag = createBagWith(
      tagManifestFiles = filesWithChecksums.map { case (f, checksumValue) =>
        BagFile(
          checksum = Checksum(SHA256, checksumValue),
          path = BagPath(f)
        )
      }
    )

    val storageManifest = createManifest(bag = bag)

    val nameChecksumMap =
      storageManifest.tagManifest.files
        .map { file => (file.name, file.checksum) }

    nameChecksumMap should contain theSameElementsAs filesWithChecksums
  }

  it("fails if one of the file manifest entries has the wrong hashing algorithm") {
    // TODO: Rewrite this to use generators
    val badBagPath = BagPath(randomAlphanumeric)
    val manifestFiles = Seq(
      BagFile(
        checksum = Checksum(SHA256, ChecksumValue(randomAlphanumeric)),
        path = BagPath(randomAlphanumeric)
      ),
      BagFile(
        checksum = Checksum(MD5, ChecksumValue(randomAlphanumeric)),
        path = badBagPath
      )
    )

    val bag = createBagWith(
      manifestFiles = manifestFiles,
      manifestChecksumAlgorithm = SHA256
    )

    assertIsError(bag = bag) {
      _ shouldBe s"Mismatched checksum algorithms in manifest: entry $badBagPath has algorithm MD5, but manifest uses SHA-256"
    }
  }

  it("fails if one of the tag manifest entries has the wrong hashing algorithm") {
    // TODO: Rewrite this to use generators
    val badBagPath = BagPath(randomAlphanumeric)
    val tagManifestFiles = Seq(
      BagFile(
        checksum = Checksum(SHA256, ChecksumValue(randomAlphanumeric)),
        path = BagPath(randomAlphanumeric)
      ),
      BagFile(
        checksum = Checksum(MD5, ChecksumValue(randomAlphanumeric)),
        path = badBagPath
      )
    )

    val bag = createBagWith(
      tagManifestFiles = tagManifestFiles,
      tagManifestChecksumAlgorithm = SHA256
    )

    assertIsError(bag = bag) {
      _ shouldBe s"Mismatched checksum algorithms in manifest: entry $badBagPath has algorithm MD5, but manifest uses SHA-256"
    }
  }

  // TEST: If the fetch entry is in wrong namespace, reject
  // TEST: If the fetch entry is in the wrong path, reject
  // TEST: Applies the right version prefix to fetch files
  // TEST: Correct storage space
  // TEST: Correct bagInfo
  // TEST: Correct version
  // TEST: Recent createdDate

  private def createManifest(
    bag: Bag = createBag,
    replicaRoot: ObjectLocation = createObjectLocation.join("/v1"),
    version: Int = 1
  ): StorageManifest =
    StorageManifestService.createManifest(
      bag = bag,
      replicaRootLocation = replicaRoot,
      version = version
    ).success.value

  private def assertIsError(
    bag: Bag = createBag,
    replicaRootLocation: ObjectLocation = createObjectLocation.join("/v1"),
    version: Int = 1
  )(assertMessage: String => Assertion): Assertion = {
    val result = StorageManifestService.createManifest(
      bag = bag,
      replicaRootLocation = replicaRootLocation,
      version = version
    )

    result.failure.exception shouldBe a[StorageManifestException]
    assertMessage(result.failure.exception.getMessage)
  }
}
