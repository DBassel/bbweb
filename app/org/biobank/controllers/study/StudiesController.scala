package org.biobank.controllers.study

import javax.inject.{Inject, Singleton}
import org.biobank.controllers._
import org.biobank.domain._
import org.biobank.domain.study._
import org.biobank.infrastructure._
import org.biobank.infrastructure.command.StudyCommands._
import org.biobank.service._
import org.biobank.service.studies.StudiesService
import org.biobank.service.users.UsersService
import play.api.Logger
import play.api.libs.json._
import play.api.mvc._
import play.api.{ Environment, Logger }
import scala.concurrent.{ExecutionContext, Future}
import scala.language.reflectiveCalls
import scalaz.Scalaz._
import scalaz.Validation.FlatMap._

/**
 *
 */
@Singleton
class StudiesController @Inject() (val action:         BbwebAction,
                                   val env:            Environment,
                                   val authToken:      AuthToken,
                                   val usersService:   UsersService,
                                   val studiesService: StudiesService)
                               (implicit ec: ExecutionContext)
    extends CommandController
    with JsonController {

  val log = Logger(this.getClass)

  private val PageSizeMax = 10

  def studyCounts() =
    action(parse.empty) { implicit request =>
      Ok(studiesService.getCountsByStatus)
    }

  def list =
    action.async(parse.empty) { implicit request =>
      Future {
        val validation = for {
            pagedQuery <- PagedQuery.create(request.rawQueryString, PageSizeMax)
            studies    <- studiesService.getStudies(pagedQuery.filter, pagedQuery.sort)
            validPage  <- pagedQuery.validPage(studies.size)
            results    <- PagedResults.create(studies, pagedQuery.page, pagedQuery.limit)
          } yield results

        validation.fold(
          err => BadRequest(err.list.toList.mkString),
          results =>  Ok(results)
        )
      }
    }

  def listNames(filterMaybe: Option[String], orderMaybe:  Option[String]) =
    action.async(parse.empty) { implicit request =>
      Future {
        val filter = filterMaybe.fold { "" } { f => f }
        val order  = orderMaybe.fold { "asc" } { o => o }

        SortOrder.fromString(order).fold(
          err => BadRequest(err.list.toList.mkString),
          so  => Ok(studiesService.getStudyNames(filter, so))
        )
      }
    }

  def get(id: StudyId) = action(parse.empty) { implicit request =>
      validationReply(studiesService.getStudy(id))
    }

  def centresForStudy(studyId: StudyId) = action(parse.empty) { implicit request =>
      Ok(studiesService.getCentresForStudy(studyId))
    }

  def add() = commandActionAsync { cmd: AddStudyCmd =>
      processCommand(cmd)
    }

  def updateName(id: StudyId) =
    commandActionAsync(Json.obj("id" -> id)) { cmd : UpdateStudyNameCmd => processCommand(cmd) }

  def updateDescription(id: StudyId) =
    commandActionAsync(Json.obj("id" -> id)) { cmd : UpdateStudyDescriptionCmd => processCommand(cmd) }

  def addAnnotationType(id: StudyId) =
    commandActionAsync(Json.obj("id" -> id)) { cmd : StudyAddParticipantAnnotationTypeCmd => processCommand(cmd) }

  def updateAnnotationType(id: StudyId, uniqueId: String) =
    commandActionAsync(Json.obj("id" -> id, "uniqueId" -> uniqueId)) {
      cmd : StudyUpdateParticipantAnnotationTypeCmd => processCommand(cmd)
    }

  def removeAnnotationType(studyId: StudyId, ver: Long, uniqueId: String) =
    action.async(parse.empty) { implicit request =>
      val cmd = UpdateStudyRemoveAnnotationTypeCmd(Some(request.authInfo.userId.id), studyId.id, ver, uniqueId)
      processCommand(cmd)
    }

  def enable(id: StudyId) =
    commandActionAsync(Json.obj("id" -> id)) { cmd: EnableStudyCmd => processCommand(cmd) }

  def disable(id: StudyId) =
    commandActionAsync(Json.obj("id" -> id)) { cmd: DisableStudyCmd => processCommand(cmd) }

  def retire(id: StudyId) =
    commandActionAsync(Json.obj("id" -> id)) { cmd: RetireStudyCmd => processCommand(cmd) }

  def unretire(id: StudyId) =
    commandActionAsync(Json.obj("id" -> id)) { cmd: UnretireStudyCmd => processCommand(cmd) }

  def valueTypes = Action(parse.empty) { request =>
      Ok(AnnotationValueType.values.map(x => x))
    }

  def anatomicalSourceTypes = Action(parse.empty) { request =>
      Ok(AnatomicalSourceType.values.map(x => x))
    }

  def specimenTypes = Action(parse.empty) { request =>
      Ok(SpecimenType.values.map(x => x))
    }

  def preservTypes = Action(parse.empty) { request =>
      Ok(PreservationType.values.map(x => x))
    }

  def preservTempTypes = Action(parse.empty) { request =>
      Ok(PreservationTemperatureType.values.map(x => x))
    }

  /** Value types used by Specimen groups.
   *
   */
  def specimenGroupValueTypes = Action(parse.empty) { request =>
      // FIXME add container types to this response
      Ok(Map(
           "anatomicalSourceType"        -> AnatomicalSourceType.values.map(x => x),
           "preservationType"            -> PreservationType.values.map(x => x),
           "preservationTemperatureType" -> PreservationTemperatureType.values.map(x => x),
           "specimenType"                -> SpecimenType.values.map(x => x)
         ))
    }

  private def processCommand(cmd: StudyCommand) = {
    val future = studiesService.processCommand(cmd)
    validationReply(future)
  }

}
