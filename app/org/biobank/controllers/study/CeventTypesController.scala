package org.biobank.controllers.study

import javax.inject.{Inject, Singleton}
import org.biobank.controllers._
import org.biobank.domain.study.{StudyId, CollectionEventTypeId}
import org.biobank.infrastructure.command.CollectionEventTypeCommands._
import org.biobank.service.AuthToken
import org.biobank.service.studies.StudiesService
import org.biobank.service.users.UsersService
import play.api.libs.json._
import play.api.{ Environment, Logger }
import play.api.mvc.{Action, Result}
import scala.concurrent.Future

@Singleton
class CeventTypesController @Inject() (val action:         BbwebAction,
                                       val env:            Environment,
                                       val authToken:      AuthToken,
                                       val usersService:   UsersService,
                                       val studiesService: StudiesService)
    extends CommandController
    with JsonController {

  val log: Logger = Logger(this.getClass)

  def get(studyId: StudyId, ceventTypeId: Option[CollectionEventTypeId]): Action[Unit] =
    action(parse.empty) { implicit request =>
      log.debug(s"CeventTypeController.list: studyId: $studyId, ceventTypeId: $ceventTypeId")

      ceventTypeId.fold {
        validationReply(studiesService.collectionEventTypesForStudy(studyId).map(_.toList))
      } { id =>
        validationReply(studiesService.collectionEventTypeWithId(studyId, id))
      }
    }

  def add(studyId: StudyId): Action[JsValue] =
    commandActionAsync(Json.obj("studyId" -> studyId)) {
      cmd: AddCollectionEventTypeCmd => processCommand(cmd) }

  def remove(studyId: StudyId, id: CollectionEventTypeId, ver: Long): Action[Unit] =
    action.async(parse.empty) { implicit request =>
      val cmd = RemoveCollectionEventTypeCmd(Some(request.authInfo.userId.id), studyId.id, id.id, ver)
      val future = studiesService.processRemoveCollectionEventTypeCommand(cmd)
      validationReply(future)
  }

  def updateName(id: CollectionEventTypeId): Action[JsValue] =
    commandActionAsync(Json.obj("id" -> id)) {
      cmd: UpdateCollectionEventTypeNameCmd => processCommand(cmd) }

  def updateDescription(id: CollectionEventTypeId): Action[JsValue] =
    commandActionAsync(Json.obj("id" -> id)) {
      cmd: UpdateCollectionEventTypeDescriptionCmd => processCommand(cmd) }

  def updateRecurring(id: CollectionEventTypeId): Action[JsValue] =
    commandActionAsync(Json.obj("id" -> id)) {
      cmd: UpdateCollectionEventTypeRecurringCmd => processCommand(cmd) }

  def addAnnotationType(id: CollectionEventTypeId): Action[JsValue] =
    commandActionAsync(Json.obj("id" -> id)) {
        cmd: CollectionEventTypeAddAnnotationTypeCmd => processCommand(cmd) }

  def updateAnnotationType(id: CollectionEventTypeId, uniqueId: String): Action[JsValue] =
    commandActionAsync(Json.obj("id" -> id, "uniqueId" -> uniqueId)) {
        cmd: CollectionEventTypeUpdateAnnotationTypeCmd => processCommand(cmd) }

  def removeAnnotationType(studyId: StudyId, id: CollectionEventTypeId, ver: Long, uniqueId: String)
      : Action[Unit]=
    action.async(parse.empty) { implicit request =>
      val cmd = RemoveCollectionEventTypeAnnotationTypeCmd(
          userId                = Some(request.authInfo.userId.id),
          studyId               = studyId.id,
          id                    = id.id,
          expectedVersion       = ver,
          uniqueId              = uniqueId)
      processCommand(cmd)
    }

  def addSpecimenSpec(id: CollectionEventTypeId): Action[JsValue] =
    commandActionAsync(Json.obj("id" -> id)) {
      cmd: AddCollectionSpecimenSpecCmd => processCommand(cmd) }

  def updateSpecimenSpec(id: CollectionEventTypeId, uniqueId: String): Action[JsValue] =
    commandActionAsync(Json.obj("id" -> id, "uniqueId" -> uniqueId)) {
      cmd: UpdateCollectionSpecimenSpecCmd => processCommand(cmd) }

  def removeSpecimenSpec(studyId: StudyId, id: CollectionEventTypeId, ver: Long, uniqueId: String)
      : Action[Unit]=
    action.async(parse.empty) { implicit request =>
      val cmd = RemoveCollectionSpecimenSpecCmd(
          userId                = Some(request.authInfo.userId.id),
          studyId               = studyId.id,
          id                    = id.id,
          expectedVersion       = ver,
          uniqueId              = uniqueId)
      processCommand(cmd)
    }

  def inUse(id: CollectionEventTypeId): Action[Unit] =
    action(parse.empty) { implicit request =>
      validationReply(studiesService.collectionEventTypeInUse(id))
    }

  private def processCommand(cmd: CollectionEventTypeCommand): Future[Result] = {
    val future = studiesService.processCollectionEventTypeCommand(cmd)
    validationReply(future)
  }
}
