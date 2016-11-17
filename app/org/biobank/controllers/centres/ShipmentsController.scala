package org.biobank.controllers.centres

import javax.inject.{Inject, Singleton}
import play.api.libs.json._
import play.api.{Environment, Logger}
import org.biobank.controllers.{BbwebAction, CommandController, JsonController, PagedQuery}
import org.biobank.domain.centre.{CentreId, ShipmentId}
import org.biobank.service.centres.ShipmentsService
import org.biobank.service.users.UsersService
import org.biobank.service.{AuthToken, PagedResults}
import scala.concurrent.{ExecutionContext, Future}
import scala.language.reflectiveCalls
import scalaz.Scalaz._
import scalaz.Validation.FlatMap._

/**
 *  Uses [[http://labs.omniti.com/labs/jsend JSend]] format for JSon replies.
 */
@Singleton
class ShipmentsController @Inject() (val action:           BbwebAction,
                                     val env:              Environment,
                                     val authToken:        AuthToken,
                                     val usersService:     UsersService,
                                     val shipmentsService: ShipmentsService)
                                 (implicit ec: ExecutionContext)
    extends CommandController
    with JsonController {

  import org.biobank.infrastructure.command.ShipmentCommands._
  import org.biobank.infrastructure.command.ShipmentSpecimenCommands._

  val log = Logger(this.getClass)

  private val PageSizeMax = 10

  def list(centreId: CentreId) =
    action.async(parse.empty) { implicit request =>
      Future {
        val validation = for {
            pagedQuery <- PagedQuery.create(request.rawQueryString, PageSizeMax)
            shipments  <- shipmentsService.getShipments(centreId, pagedQuery.filter, pagedQuery.sort)
            validPage  <- pagedQuery.validPage(shipments.size)
            results    <- PagedResults.create(shipments, pagedQuery.page, pagedQuery.limit)
          } yield results

        validation.fold(
          err =>     BadRequest(err.toList.mkString),
          results => Ok(results)
        )
      }
    }

  def get(id: ShipmentId) =
    action(parse.empty) { implicit request =>
      validationReply(shipmentsService.getShipment(id))
    }

  def listSpecimens(shipmentId: ShipmentId) =
    action.async(parse.empty) { implicit request =>
      Future {
        val validation = for {
            pagedQuery        <- PagedQuery.create(request.rawQueryString, PageSizeMax)
            shipmentSpecimens <- shipmentsService.getShipmentSpecimens(shipmentId,
                                                                       pagedQuery.filter,
                                                                       pagedQuery.sort)
            validPage         <- pagedQuery.validPage(shipmentSpecimens.size)
            results           <- PagedResults.create(shipmentSpecimens,
                                                     pagedQuery.page,
                                                     pagedQuery.limit)
          } yield results

        validation.fold(
          err =>     BadRequest(err.list.toList.mkString),
          results => Ok(results)
        )
      }
    }

  def canAddSpecimen(shipmentId: ShipmentId, specimenInventoryId: String) =
    action(parse.empty) { implicit request =>
      validationReply(shipmentsService.shipmentCanAddSpecimen(shipmentId, specimenInventoryId))
    }

  def getSpecimen(shipmentId: ShipmentId, shipmentSpecimenId: String) =
    action(parse.empty) { implicit request =>
      validationReply(shipmentsService.getShipmentSpecimen(shipmentId, shipmentSpecimenId))
    }

  def add() = commandActionAsync { cmd: AddShipmentCmd => processCommand(cmd) }

  def remove(shipmentId: ShipmentId, version: Long) =
    action.async(parse.empty) { implicit request =>
      val cmd = ShipmentRemoveCmd(userId          = request.authInfo.userId.id,
                                  id              = shipmentId.id,
                                  expectedVersion = version)
      val future = shipmentsService.removeShipment(cmd)
      validationReply(future)
    }

  def updateCourier(id: ShipmentId) =
    commandActionAsync(Json.obj("id" -> id)) { cmd : UpdateShipmentCourierNameCmd => processCommand(cmd) }

  def updateTrackingNumber(id: ShipmentId) =
    commandActionAsync(Json.obj("id" -> id)) { cmd : UpdateShipmentTrackingNumberCmd => processCommand(cmd) }

  def updateFromLocation(id: ShipmentId) =
    commandActionAsync(Json.obj("id" -> id)) { cmd : UpdateShipmentFromLocationCmd => processCommand(cmd) }

  def updateToLocation(id: ShipmentId) =
    commandActionAsync(Json.obj("id" -> id)) { cmd : UpdateShipmentToLocationCmd => processCommand(cmd) }

  def created(id: ShipmentId) =
    commandActionAsync(Json.obj("id" -> id)) { cmd : CreatedShipmentCmd => processCommand(cmd) }

  def packed(id: ShipmentId) =
    commandActionAsync(Json.obj("id" -> id)) { cmd : PackShipmentCmd => processCommand(cmd) }

  def sent(id: ShipmentId) =
    commandActionAsync(Json.obj("id" -> id)) { cmd : SendShipmentCmd => processCommand(cmd) }

  def received(id: ShipmentId) =
    commandActionAsync(Json.obj("id" -> id)) { cmd : ReceiveShipmentCmd => processCommand(cmd) }

  def unpacked(id: ShipmentId) =
    commandActionAsync(Json.obj("id" -> id)) { cmd : UnpackShipmentCmd => processCommand(cmd) }

  def lost(id: ShipmentId) =
    commandActionAsync(Json.obj("id" -> id)) { cmd : LostShipmentCmd => processCommand(cmd) }

  /**
   * Changes the state of a shipment from CREATED to SENT (skipping the PACKED state)
   */
  def skipStateSent(id: ShipmentId) =
    commandActionAsync(Json.obj("id" -> id)) { cmd : ShipmentSkipStateToSentCmd => processCommand(cmd) }

  /**
   * Changes the state of a shipment from SENT to UNPACKED (skipping the RECEVIED state)
   */
  def skipStateUnpacked(id: ShipmentId) =
    commandActionAsync(Json.obj("id" -> id)) { cmd : ShipmentSkipStateToUnpackedCmd => processCommand(cmd) }

  def addSpecimen(shipmentId: ShipmentId) = commandActionAsync(Json.obj("shipmentId" -> shipmentId)) {
      cmd: ShipmentSpecimenAddCmd => processSpecimenCommand(cmd)
    }

  def removeSpecimen(shipmentId: ShipmentId, shipmentSpecimenId: String, version: Long) =
    action.async(parse.empty) { implicit request =>
      val cmd = ShipmentSpecimenRemoveCmd(userId          = request.authInfo.userId.id,
                                          shipmentId      = shipmentId.id,
                                          id              = shipmentSpecimenId,
                                          expectedVersion = version)
      val future = shipmentsService.removeShipmentSpecimen(cmd)
      validationReply(future)
    }

  def specimenContainer(shipmentId: ShipmentId, shipmentSpecimenId: String) =
    commandActionAsync(Json.obj("shipmentId" -> shipmentId, "id" -> shipmentSpecimenId)) {
      cmd: ShipmentSpecimenUpdateContainerCmd => processSpecimenCommand(cmd)
    }

  def specimenReceived(shipmentId: ShipmentId, shipmentSpecimenId: String) =
    commandActionAsync(Json.obj("shipmentId" -> shipmentId, "id" -> shipmentSpecimenId)) {
      cmd: ShipmentSpecimenReceivedCmd => processSpecimenCommand(cmd)
    }

  def specimenMissing(shipmentId: ShipmentId, shipmentSpecimenId: String) =
    commandActionAsync(Json.obj("shipmentId" -> shipmentId, "id" -> shipmentSpecimenId)) {
      cmd: ShipmentSpecimenMissingCmd => processSpecimenCommand(cmd)
    }

  def specimenExtra(shipmentId: ShipmentId, shipmentSpecimenId: String) =
    commandActionAsync(Json.obj("shipmentId" -> shipmentId, "id" -> shipmentSpecimenId)) {
      cmd: ShipmentSpecimenExtraCmd => processSpecimenCommand(cmd)
    }

  private def processCommand(cmd: ShipmentCommand) = {
    val future = shipmentsService.processCommand(cmd)
    validationReply(future)
  }

  private def processSpecimenCommand(cmd: ShipmentSpecimenCommand) = {
    val future = shipmentsService.processShipmentSpecimenCommand(cmd)
    validationReply(future)
  }
}
