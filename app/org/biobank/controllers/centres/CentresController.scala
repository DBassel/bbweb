package org.biobank.controllers.centres

import javax.inject.{Inject, Singleton}
import org.biobank.controllers._
import org.biobank.domain.centre.CentreId
import org.biobank.infrastructure.command.CentreCommands._
import org.biobank.service._
import org.biobank.service.centres.CentresService
import org.biobank.service.users.UsersService
import play.api.libs.json._
import play.api.mvc._
import play.api.{ Environment, Logger }
import scala.concurrent.{ExecutionContext, Future}
import scalaz.Scalaz._
import scalaz.Validation.FlatMap._

/**
 *  Uses [[http://labs.omniti.com/labs/jsend JSend]] format for JSon replies.
 */
@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
@Singleton
class CentresController @Inject() (val action:         BbwebAction,
                                   val env:            Environment,
                                   val authToken:      AuthToken,
                                   val usersService:   UsersService,
                                   val centresService: CentresService)
                               (implicit ec: ExecutionContext)
    extends CommandController
    with JsonController {

  val log: Logger = Logger(this.getClass)

  private val PageSizeMax = 10

  def centreCounts(): Action[Unit] =
    action(parse.empty) { implicit request =>
      Ok(centresService.getCountsByStatus)
    }

  def list: Action[Unit] =
    action.async(parse.empty) { implicit request =>
      validationReply(
        Future {
          for {
            pagedQuery <- PagedQuery.create(request.rawQueryString, PageSizeMax)
            centres    <- centresService.getCentres(pagedQuery.filter, pagedQuery.sort)
            validPage  <- pagedQuery.validPage(centres.size)
            results    <- PagedResults.create(centres, pagedQuery.page, pagedQuery.limit)
          } yield results
        }
      )
    }

  def listNames: Action[Unit] =
    action.async(parse.empty) { implicit request =>
      validationReply(
        Future {
          for {
            filterAndSort <- FilterAndSortQuery.create(request.rawQueryString)
            centreNames    <- centresService.getCentreNames(filterAndSort.filter, filterAndSort.sort)
          } yield centreNames
        }
      )
    }

  // def locations() = action(parse.empty) { implicit request =>
  //     Ok(centresService.centreLocations)
  // }

  def searchLocations(): Action[JsValue] =
    commandAction { cmd: SearchCentreLocationsCmd =>
      Ok(centresService.searchLocations(cmd))
    }

  def query(id: CentreId): Action[Unit] =
    action(parse.empty) { implicit request =>
      validationReply(centresService.getCentre(id))
    }

  def add(): Action[JsValue] = commandActionAsync { cmd: AddCentreCmd => processCommand(cmd) }

  def updateName(id: CentreId): Action[JsValue] =
    commandActionAsync(Json.obj("id" -> id)) { cmd : UpdateCentreNameCmd => processCommand(cmd) }

  def updateDescription(id: CentreId): Action[JsValue] =
    commandActionAsync(Json.obj("id" -> id)) { cmd : UpdateCentreDescriptionCmd => processCommand(cmd) }

  def addStudy(centreId: CentreId): Action[JsValue] =
    commandActionAsync(Json.obj("id" -> centreId)) { cmd : AddStudyToCentreCmd => processCommand(cmd) }

  def removeStudy(centreId: CentreId, ver: Long, studyId: String): Action[Unit] =
    action.async(parse.empty) { implicit request =>
      processCommand(RemoveStudyFromCentreCmd(request.authInfo.userId.id, centreId.id, ver, studyId))
    }

  def addLocation(id: CentreId): Action[JsValue] =
    commandActionAsync(Json.obj("id" -> id)) { cmd : AddCentreLocationCmd => processCommand(cmd) }

  def updateLocation(id: CentreId, locationId: String): Action[JsValue] =
    commandActionAsync(Json.obj("id"         -> id,
                                "locationId" -> locationId)) { cmd : UpdateCentreLocationCmd =>
      processCommand(cmd)
    }

  def removeLocation(centreId: CentreId, ver: Long, locationId: String): Action[Unit] =
    action.async(parse.empty) { implicit request =>
      processCommand(RemoveCentreLocationCmd(request.authInfo.userId.id, centreId.id, ver, locationId))
    }

  def enable(id: CentreId): Action[JsValue] =
    commandActionAsync(Json.obj("id" -> id)) { cmd : EnableCentreCmd => processCommand(cmd) }

  def disable(id: CentreId): Action[JsValue] =
    commandActionAsync(Json.obj("id" -> id)) { cmd : DisableCentreCmd => processCommand(cmd) }

  private def processCommand(cmd: CentreCommand): Future[Result] = {
    val future = centresService.processCommand(cmd)
    validationReply(future)
  }

}
