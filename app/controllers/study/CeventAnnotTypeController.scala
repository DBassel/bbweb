package controllers.study

import controllers._
import service._
import infrastructure._
import service.{ ServiceComponent, ServiceComponentImpl }
import infrastructure.command.StudyCommands._
import domain._
import domain.study._
import AnnotationValueType._
import views._

import collection.immutable.ListMap
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps
import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.templates._
import play.api.i18n.Messages
import play.Logger
import akka.util.Timeout
import securesocial.core.{ Authorization, Identity, SecuredRequest, SecureSocial }

import scalaz._
import Scalaz._

/**
 * Used to map the form inputs into an object that can be saved via a service call.
 */
case class CeventAnnotationTypeMapper(
  annotationTypeId: String,
  version: Long,
  studyId: String,
  studyName: String,
  name: String,
  description: Option[String],
  valueType: String,
  maxValueCount: Option[Int],
  selections: List[String])
  extends StudyAnnotationTypeMapper {

  def getAddCmd: AddCollectionEventAnnotationTypeCmd = {
    val selectionMap = if (selections.size > 0) Some(selections.map(v => (v, v)).toMap) else None
    AddCollectionEventAnnotationTypeCmd(studyId, name, description,
      AnnotationValueType.withName(valueType), maxValueCount, selectionMap)
  }

  def getUpdateCmd: UpdateCollectionEventAnnotationTypeCmd = {
    val selectionMap = if (selections.size > 0) Some(selections.map(v => (v, v)).toMap) else None
    UpdateCollectionEventAnnotationTypeCmd(
      annotationTypeId, Some(version), studyId, name, description,
      AnnotationValueType.withName(valueType), maxValueCount, selectionMap)
  }
}

object CeventAnnotTypeController
  extends StudyAnnotationTypeController[CollectionEventAnnotationType] {

  val annotationTypeForm = Form(
    mapping(
      "annotationTypeId" -> text,
      "version" -> longNumber,
      "studyId" -> text,
      "studyName" -> text,
      "name" -> nonEmptyText,
      "description" -> optional(text),
      "valueType" -> nonEmptyText,
      "maxValueCount" -> optional(number),
      "selections" -> list(text))(CeventAnnotationTypeMapper.apply)(CeventAnnotationTypeMapper.unapply))

  override protected def studyBreadcrumbs(studyId: String, studyName: String) = {
    Map(
      (Messages("biobank.study.plural") -> routes.StudyController.index),
      (studyName -> routes.StudyController.showStudy(studyId)))
  }

  override protected def addBreadcrumbs(studyId: String, studyName: String) = {
    studyBreadcrumbs(studyId, studyName) +
      (Messages("biobank.study.collection.event.annotation.type.add") -> null)
  }

  override protected def updateBreadcrumbs(studyId: String, studyName: String) = {
    studyBreadcrumbs(studyId, studyName) +
      (Messages("biobank.study.collection.event.annotation.type.update") -> null)
  }

  override protected def removeBreadcrumbs(studyId: String, studyName: String) = {
    studyBreadcrumbs(studyId, studyName) +
      (Messages("biobank.study.collection.event.annotation.type.remove") -> null)
  }

  override protected def addTitle: String =
    Messages("biobank.study.collection.event.annotation.type.add")

  override protected def updateTitle: String =
    Messages("biobank.study.collection.event.annotation.type.update")

  override protected def addAction: Call =
    routes.CeventAnnotTypeController.addAnnotationTypeSubmit

  override protected def updateAction: Call =
    routes.CeventAnnotTypeController.updateAnnotationTypeSubmit

  override protected def annotationTypeInUseErrorMsg(annotName: String): String =
    Messages("biobank.study.collection.event.annotation.type.in.use.error.message", annotName)

  override protected def isAnnotationTypeInUse(
    studyId: String,
    annotationTypeId: String): DomainValidation[Boolean] = {
    studyService.isCollectionEventAnnotationTypeInUse(studyId, annotationTypeId)
  }

  /**
   * Add an attribute type.
   */
  def addAnnotationType(
    studyId: String,
    studyName: String) = SecuredAction { implicit request =>
    super.addAnnotationType(studyId, studyName)(study =>
      Ok(html.study.annotationtype.add(
        annotationTypeForm, AddFormType(), studyId, study.name,
        Messages("biobank.study.collection.event.annotation.type.add"),
        routes.CeventAnnotTypeController.addAnnotationTypeSubmit,
        annotationValueTypes,
        addBreadcrumbs(studyId, studyName))))
  }

  def addAnnotationTypeSubmit = SecuredAction.async { implicit request =>
    implicit val userId = new UserId(request.user.identityId.userId)
    super.addAnnotationTypeSubmit(annotationTypeForm) {
      formObj =>
        val studyId = formObj.studyId
        val studyName = formObj.studyName

        studyService.addCollectionEventAnnotationType(formObj.getAddCmd).map(validation =>
          validation match {
            case Failure(x) =>
              if (x.head.contains("name already exists")) {
                val form = annotationTypeForm.fill(formObj).withError("name",
                  Messages("biobank.study.collection.event.annotation.type.form.error.name"))
                BadRequest(html.study.annotationtype.add(
                  form,
                  AddFormType(),
                  studyId,
                  studyName,
                  Messages("biobank.study.collection.event.annotation.type.add"),
                  routes.CeventAnnotTypeController.addAnnotationTypeSubmit,
                  annotationValueTypes,
                  addBreadcrumbs(studyId, studyName)))
              } else {
                throw new Error(x.head)
              }
            case Success(annotType) =>
              Redirect(routes.StudyController.showStudy(studyId)).flashing(
                "success" -> Messages("biobank.annotation.type.added", annotType.name))
          })
    }
  }

  def updateAnnotationType(
    studyId: String,
    studyName: String,
    annotationTypeId: String) = SecuredAction { implicit request =>
    val annotationType = studyService.collectionEventAnnotationTypeWithId(studyId, annotationTypeId)
    super.updateAnnotationType(studyId, studyName, annotationType) {
      (studyId, studyName, annotationType) =>
        studyService.collectionEventAnnotationTypeWithId(studyId, annotationTypeId) match {
          case Failure(x) => throw new Error(x.head)
          case Success(annotType) =>
            val form = annotationTypeForm.fill(CeventAnnotationTypeMapper(
              annotType.id.id, annotType.version, studyId, studyName, annotType.name,
              annotType.description, annotType.valueType.toString, annotType.maxValueCount,
              annotType.options.map(v => v.values.toList).getOrElse(List.empty)))
            Ok(html.study.annotationtype.add(form, UpdateFormType(), studyId, studyName,
              Messages("biobank.study.collection.event.annotation.type.update"),
              routes.CeventAnnotTypeController.updateAnnotationTypeSubmit,
              annotationValueTypes,
              updateBreadcrumbs(studyId, studyName)))
        }
    }
  }

  def updateAnnotationTypeSubmit = SecuredAction.async { implicit request =>
    implicit val userId = new UserId(request.user.identityId.userId)
    super.updateAnnotationTypeSubmit(annotationTypeForm) {
      submittedForm =>
        val studyId = submittedForm.studyId
        val studyName = submittedForm.studyName

        studyService.updateCollectionEventAnnotationType(submittedForm.getUpdateCmd).map(validation =>
          validation match {
            case Failure(x) =>
              if (x.head.contains("name already exists")) {
                val form = annotationTypeForm.fill(submittedForm).withError("name",
                  Messages("biobank.study.collection.event.annotation.type.form.error.name"))
                BadRequest(html.study.annotationtype.add(
                  form, UpdateFormType(), studyId, studyName,
                  Messages("biobank.study.collection.event.annotation.type.update"),
                  routes.CeventAnnotTypeController.updateAnnotationTypeSubmit,
                  annotationValueTypes,
                  updateBreadcrumbs(studyId, studyName)))
              } else {
                throw new Error(x.head)
              }
            case Success(annotType) =>
              Redirect(routes.StudyController.showStudy(studyId)).flashing(
                "success" -> Messages("biobank.annotation.type.updated", annotType.name))
          })
    }
  }

  def removeAnnotationType(
    studyId: String,
    studyName: String,
    annotationTypeId: String) = SecuredAction {
    implicit request =>
      val annotationType = studyService.collectionEventAnnotationTypeWithId(studyId, annotationTypeId)
      super.removeAnnotationType(studyId, studyName, annotationType) {
        (studyId, studyName, annotationType) =>
          studyService.collectionEventAnnotationTypeWithId(studyId, annotationTypeId) match {
            case Failure(x) => throw new Error(x.head)
            case Success(annotType) =>
              Ok(html.study.annotationtype.removeConfirm(studyId, studyName,
                Messages("biobank.study.collection.event.type.remove"),
                Messages("biobank.study.collection.event.group.remove.confirm", annotType.name),
                annotType,
                annotationTypeFieldsMap(annotationType), removeBreadcrumbs(studyId, studyName)))
          }
      }
  }

  def removeAnnotationTypeSubmit = SecuredAction.async { implicit request =>
    implicit val userId = new UserId(request.user.identityId.userId)
    super.removeAnnotationTypeSubmit {
      (studyId, studyName, annotationTypeId) =>
        studyService.collectionEventAnnotationTypeWithId(studyId, annotationTypeId) match {
          case Failure(x) => throw new Error(x.head)
          case Success(annotType) =>
            studyService.removeCollectionEventAnnotationType(
              RemoveCollectionEventAnnotationTypeCmd(
                annotType.id.id, annotType.versionOption, studyId)).map(validation =>
                validation match {
                  case Success(at) =>
                    Redirect(routes.StudyController.showStudy(studyId)).flashing(
                      "success" -> Messages("biobank.study.collection.event.annotation.type.removed", annotType.name))
                  case Failure(x) =>
                    throw new Error(x.head)
                })
        }
    }

  }
}
