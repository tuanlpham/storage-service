package uk.ac.wellcome.platform.storage.bags.api

import java.net.URL

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Route
import io.circe.Printer
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.archive.common.bagit.models.{
  BagId,
  ExternalIdentifier
}
import uk.ac.wellcome.platform.archive.common.storage.models.StorageSpace
import uk.ac.wellcome.platform.archive.common.storage.services.StorageManifestVHS
import uk.ac.wellcome.platform.storage.bags.api.models.DisplayBag

import scala.concurrent.ExecutionContext

class Router(vhs: StorageManifestVHS, contextURL: URL)(
  implicit val ec: ExecutionContext) {

  def routes: Route = {
    import akka.http.scaladsl.server.Directives._
    import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
    implicit val printer: Printer = Printer.noSpaces.copy(dropNullValues = true)

    pathPrefix("registrar") {
      path(Segment / Segment) { (space, externalIdentifier) =>
        val bagId = BagId(
          space = StorageSpace(space),
          externalIdentifier = ExternalIdentifier(externalIdentifier)
        )

        get {
          onSuccess(vhs.getRecord(bagId)) {
            case Some(storageManifest) =>
              complete(DisplayBag(storageManifest, contextURL))
            case None => complete(NotFound -> "Storage manifest not found!")
          }
        }
      }
    }
  }
}