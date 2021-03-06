package org.biobank.infrastructure.command

import org.biobank.infrastructure.command.Commands._
import org.joda.time.DateTime
import play.api.libs.json._

object ShipmentCommands {

  import org.biobank.infrastructure.JsonUtils._

  trait ShipmentCommand extends Command with HasUserId

  trait ShipmentModifyCommand extends ShipmentCommand with HasIdentity with HasExpectedVersion

  trait ShipmentCommandWithShipmentId extends ShipmentCommand with HasShipmentIdentity

  final case class AddShipmentCmd(userId:         String,
                                  courierName:    String,
                                  trackingNumber: String,
                                  fromLocationId: String,
                                  toLocationId:   String)
      extends ShipmentCommand

  final case class UpdateShipmentCourierNameCmd(userId:          String,
                                                id:              String, // shipment ID
                                                expectedVersion: Long,
                                                courierName:     String)
      extends ShipmentModifyCommand

  final case class UpdateShipmentTrackingNumberCmd(userId:          String,
                                                   id:              String, // shipment ID
                                                   expectedVersion: Long,
                                                   trackingNumber:  String)
      extends ShipmentModifyCommand

  final case class UpdateShipmentFromLocationCmd(userId:          String,
                                                 id:              String, // shipment ID
                                                 expectedVersion: Long,
                                                 locationId:      String)
      extends ShipmentModifyCommand

  final case class UpdateShipmentToLocationCmd(userId:          String,
                                               id:              String, // shipment ID
                                               expectedVersion: Long,
                                               locationId:      String)
      extends ShipmentModifyCommand

  final case class CreatedShipmentCmd(userId:          String,
                                      id:              String, // shipment ID
                                      expectedVersion: Long)
      extends ShipmentModifyCommand

  final case class PackShipmentCmd(userId:          String,
                                   id:              String, // shipment ID
                                   expectedVersion: Long,
                                   datetime:        DateTime)
      extends ShipmentModifyCommand

  final case class SendShipmentCmd(userId:          String,
                                   id:              String, // shipment ID
                                   expectedVersion: Long,
                                   datetime:        DateTime)
      extends ShipmentModifyCommand

  final case class ReceiveShipmentCmd(userId:       String,
                                   id:              String, // shipment ID
                                   expectedVersion: Long,
                                   datetime:        DateTime)
      extends ShipmentModifyCommand

  final case class UnpackShipmentCmd(userId:          String,
                                     id:              String, // shipment ID
                                     expectedVersion: Long,
                                     datetime:        DateTime)
      extends ShipmentModifyCommand

  final case class LostShipmentCmd(userId:          String,
                                   id:              String, // shipment ID
                                   expectedVersion: Long)
      extends ShipmentModifyCommand

  final case class ShipmentSkipStateToSentCmd(userId:          String,
                                              id:              String, // shipment ID
                                              expectedVersion: Long,
                                              timePacked:      DateTime,
                                              timeSent:        DateTime)
      extends ShipmentModifyCommand

  final case class ShipmentSkipStateToUnpackedCmd(userId:          String,
                                                  id:              String, // shipment ID
                                                  expectedVersion: Long,
                                                  timeReceived:    DateTime,
                                                  timeUnpacked:    DateTime)
      extends ShipmentModifyCommand

  final case class ShipmentRemoveCmd(userId:          String,
                                     id:              String, // shipment ID
                                     expectedVersion: Long)
      extends ShipmentModifyCommand

  implicit val addShipmentCmdReads: Reads[AddShipmentCmd] =
    Json.reads[AddShipmentCmd]

  implicit val updateShipmentCourierNameCmdReads: Reads[UpdateShipmentCourierNameCmd] =
    Json.reads[UpdateShipmentCourierNameCmd]

  implicit val updateShipmentTrackingNumberCmdReads: Reads[UpdateShipmentTrackingNumberCmd] =
    Json.reads[UpdateShipmentTrackingNumberCmd]

  implicit val updateShipmentFromLocationCmdReads: Reads[UpdateShipmentFromLocationCmd] =
    Json.reads[UpdateShipmentFromLocationCmd]

  implicit val updateShipmentToLocationCmdReads: Reads[UpdateShipmentToLocationCmd] =
    Json.reads[UpdateShipmentToLocationCmd]

  implicit val createdShipmentCmdReads: Reads[CreatedShipmentCmd] =
    Json.reads[CreatedShipmentCmd]

  implicit val packShipmentCmdReads: Reads[PackShipmentCmd] =
    Json.reads[PackShipmentCmd]

  implicit val sendShipmentCmdReads: Reads[SendShipmentCmd] =
    Json.reads[SendShipmentCmd]

  implicit val receiveShipmentCmdReads: Reads[ReceiveShipmentCmd] =
    Json.reads[ReceiveShipmentCmd]

  implicit val unpackShipmentCmdReads: Reads[UnpackShipmentCmd] =
    Json.reads[UnpackShipmentCmd]

  implicit val lostShipmentCmdReads: Reads[LostShipmentCmd] =
    Json.reads[LostShipmentCmd]

  implicit val shipmentSkipStateToSentCmdReads: Reads[ShipmentSkipStateToSentCmd] =
    Json.reads[ShipmentSkipStateToSentCmd]

  implicit val shipmentSkipStateToUnpackedCmdReads: Reads[ShipmentSkipStateToUnpackedCmd] =
    Json.reads[ShipmentSkipStateToUnpackedCmd]

  implicit val shipmentRemoveCmdReads: Reads[ShipmentRemoveCmd] =
    Json.reads[ShipmentRemoveCmd]

}
