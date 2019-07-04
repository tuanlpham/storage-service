package uk.ac.wellcome.platform.archive.common.storage.services

import java.nio.file.Paths

import org.scalatest.{EitherValues, FunSpec, OptionValues}
import uk.ac.wellcome.platform.archive.common.fixtures.StorageRandomThings
import uk.ac.wellcome.platform.archive.common.storage.{Locatable, LocateFailure}
import uk.ac.wellcome.storage.ObjectLocation
import uk.ac.wellcome.storage.fixtures.S3Fixtures

class S3StreamableTest
    extends FunSpec
    with S3Fixtures
    with OptionValues
    with EitherValues
    with StorageRandomThings {

  import S3StreamableInstances._

  case class Thing(stuff: String)

  implicit val thingResolver: Locatable[Thing] = new Locatable[Thing] {
    override def locate(thing: Thing)(root: Option[ObjectLocation])
      : Either[LocateFailure[Thing], ObjectLocation] = {
      val paths = Paths.get(root.get.path, thing.stuff)
      Right(root.get.copy(path = paths.toString))
    }
  }

  describe("converts to a Either[LocateFailure[Thing], ObjectLocation]") {
    it("produces a failure from an invalid root") {

      val invalidRoot = ObjectLocation(
        "invalid_bucket",
        "invalid.key"
      )

      val myThing = Thing(randomAlphanumericWithLength())

      val myStream = myThing.locateWith(invalidRoot)

      myStream.left.value.msg should include(
        "The specified bucket is not valid")
    }

    it("produces a Right[Some[Thing]] from a valid ObjectLocation") {
      withLocalS3Bucket { bucket =>
        val key = randomAlphanumericWithLength()
        val thingStuff = randomAlphanumericWithLength()

        s3Client.putObject(bucket.name, s"$key/$thingStuff", thingStuff)

        val validRoot = ObjectLocation(
          bucket.name,
          key
        )

        val myThing = Thing(thingStuff)

        val inputStream = myThing.locateWith(validRoot).right.value.get

        scala.io.Source
          .fromInputStream(inputStream)
          .mkString shouldEqual thingStuff
      }
    }
  }
}