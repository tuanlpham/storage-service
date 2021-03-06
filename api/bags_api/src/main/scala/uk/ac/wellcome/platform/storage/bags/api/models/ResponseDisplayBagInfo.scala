package uk.ac.wellcome.platform.storage.bags.api.models

import io.circe.generic.extras.JsonKey
import uk.ac.wellcome.platform.archive.common.bagit.models.BagInfo

case class ResponseDisplayBagInfo(
  externalIdentifier: String,
  payloadOxum: String,
  baggingDate: String,
  sourceOrganization: Option[String] = None,
  externalDescription: Option[String] = None,
  internalSenderIdentifier: Option[String] = None,
  internalSenderDescription: Option[String] = None,
  @JsonKey("type") ontologyType: String = "BagInfo"
)

object ResponseDisplayBagInfo {
  def apply(bagInfo: BagInfo): ResponseDisplayBagInfo = ResponseDisplayBagInfo(
    externalIdentifier = bagInfo.externalIdentifier.underlying,
    payloadOxum = bagInfo.payloadOxum.toString,
    baggingDate = bagInfo.baggingDate.toString,
    sourceOrganization = bagInfo.sourceOrganisation.map(_.underlying),
    externalDescription = bagInfo.externalDescription.map(_.underlying),
    internalSenderIdentifier =
      bagInfo.internalSenderIdentifier.map(_.underlying),
    internalSenderDescription =
      bagInfo.internalSenderDescription.map(_.underlying)
  )
}
