package domain.service

import org.eligosource.eventsourced.core._

import domain._
import domain.study._
import infrastructure._
import infrastructure.commands._
import infrastructure.events._
import domain.study.{
  CollectionEventType,
  CollectionEventTypeId,
  DisabledStudy,
  EnabledStudy,
  SpecimenGroup,
  SpecimenGroupId,
  Study,
  StudyId
}
import scalaz._
import scalaz.Scalaz._

class CollectionEventTypeDomainService(
  studyRepository: ReadRepository[StudyId, Study],
  collectionEventTypeRepository: ReadWriteRepository[CollectionEventTypeId, CollectionEventType],
  specimenGroupRepository: ReadRepository[SpecimenGroupId, SpecimenGroup],
  specimenGroupCollectionEventTypes: ValueObjectList[SpecimenGroupCollectionEventType]) {

  def process = PartialFunction[Any, DomainValidation[_]] {
    case _@ (study: DisabledStudy, cmd: AddCollectionEventTypeCmd, listeners: MessageEmitter) =>
      addCollectionEventType(study, cmd, listeners)
    case _@ (study: DisabledStudy, cmd: UpdateCollectionEventTypeCmd, listeners: MessageEmitter) =>
      updateCollectionEventType(study, cmd, listeners)
    case _@ (study: DisabledStudy, cmd: RemoveCollectionEventTypeCmd, listeners: MessageEmitter) =>
      removeCollectionEventType(study, cmd, listeners)
    case _@ (study: DisabledStudy, cmd: AddSpecimenGroupToCollectionEventTypeCmd, listeners: MessageEmitter) =>
      addSpecimenGroupToCollectionEventType(study, cmd, listeners)
    case _@ (study: DisabledStudy, cmd: RemoveSpecimenGroupFromCollectionEventTypeCmd, listeners: MessageEmitter) =>
      removeSpecimenGroupFromCollectionEventType(study, cmd, listeners)
    case _@ (study: DisabledStudy, cmd: AddAnnotationToCollectionEventTypeCmd, listeners: MessageEmitter) =>
      addAnnotationToCollectionEventType(study, cmd, listeners)
    case _@ (study: DisabledStudy, cmd: RemoveAnnotationFromCollectionEventTypeCmd, listeners: MessageEmitter) =>
      removeAnnotationFromCollectionEventType(study, cmd, listeners)
    case _ =>
      throw new Error("invalid command received")
  }

  private def addCollectionEventType(study: DisabledStudy,
    cmd: AddCollectionEventTypeCmd,
    listeners: MessageEmitter): DomainValidation[CollectionEventType] = {
    val collectionEventTypes = collectionEventTypeRepository.getMap.filter(
      cet => cet._2.studyId.equals(study.id))
    val v = study.addCollectionEventType(collectionEventTypes, cmd)
    v match {
      case Success(cet) =>
        collectionEventTypeRepository.updateMap(cet)
        listeners sendEvent CollectionEventTypeAddedEvent(
          cmd.studyId, cet.name, cet.description, cet.recurring)
      case _ => // nothing to do in this case
    }
    v
  }

  private def updateCollectionEventType(study: DisabledStudy, cmd: UpdateCollectionEventTypeCmd,
    listeners: MessageEmitter): DomainValidation[CollectionEventType] = {
    val collectionEventTypeId = new CollectionEventTypeId(cmd.collectionEventTypeId)
    Entity.update(collectionEventTypeRepository.getByKey(collectionEventTypeId),
      collectionEventTypeId, cmd.expectedVersion) { prevCet =>
        val cet = CollectionEventType(collectionEventTypeId, study.id, prevCet.version + 1,
          cmd.name, cmd.description, cmd.recurring)
        collectionEventTypeRepository.updateMap(cet)
        listeners sendEvent CollectionEventTypeUpdatedEvent(
          cmd.studyId, cmd.collectionEventTypeId, cet.name, cet.description, cet.recurring)
        cet.success
      }
  }

  private def removeCollectionEventType(study: DisabledStudy, cmd: RemoveCollectionEventTypeCmd,
    listeners: MessageEmitter): DomainValidation[CollectionEventType] = {
    val collectionEventTypeId = new CollectionEventTypeId(cmd.collectionEventTypeId)
    collectionEventTypeRepository.getByKey(collectionEventTypeId) match {
      case None =>
        DomainError("collection event type does not exist: %s" format cmd.collectionEventTypeId).fail
      case Some(cet) =>
        collectionEventTypeRepository.remove(cet)
        listeners sendEvent CollectionEventTypeRemovedEvent(cmd.studyId, cmd.collectionEventTypeId)
        cet.success
    }
  }

  private def validateSpecimenGroupId(study: DisabledStudy,
    specimenGroupId: String): DomainValidation[SpecimenGroup] = {
    specimenGroupRepository.getByKey(new SpecimenGroupId(specimenGroupId)) match {
      case Some(sg) =>
        if (study.id.equals(sg.studyId)) sg.success
        else DomainError("specimen group does not belong to study: %s" format specimenGroupId).fail
      case None =>
        DomainError("specimen group does not exist: %s" format specimenGroupId).fail
    }
  }

  private def validateCollectionEventTypeId(study: DisabledStudy,
    collectionEventTypeId: String): DomainValidation[CollectionEventType] = {
    collectionEventTypeRepository.getByKey(new CollectionEventTypeId(collectionEventTypeId)) match {
      case Some(cet) =>
        if (study.id.equals(cet.studyId)) cet.success
        else DomainError("collection event type does not belong to study: %s" format collectionEventTypeId).fail
      case None =>
        DomainError("collection event type does not exist: %s" format collectionEventTypeId).fail
    }
  }

  private def addSpecimenGroupToCollectionEventType(study: DisabledStudy,
    cmd: AddSpecimenGroupToCollectionEventTypeCmd,
    listeners: MessageEmitter): DomainValidation[SpecimenGroupCollectionEventType] = {
    val v1 = validateSpecimenGroupId(study, cmd.specimenGroupId)
    v1 match {
      case Success(sg) =>
        val v2 = validateCollectionEventTypeId(study, cmd.collectionEventTypeId)
        v2 match {
          case Success(cet) => {
            val sg2cet = new SpecimenGroupCollectionEventType(sg.id, cet.id, cmd.count, cmd.amount)
            listeners sendEvent SpecimenGroupAddedToCollectionEventTypeEvent(
              cmd.studyId, cmd.collectionEventTypeId, cmd.specimenGroupId, cmd.count, cmd.amount)
            sg2cet.success
          }
          case Failure(err) => v2
        }
      case Failure(_) => v1
    }
  }

  private def removeSpecimenGroupFromCollectionEventType(study: DisabledStudy,
    cmd: RemoveSpecimenGroupFromCollectionEventTypeCmd,
    listeners: MessageEmitter): DomainValidation[CollectionEventType] = {
    listeners sendEvent SpecimenGroupRemovedFromCollectionEventTypeEvent(
      cmd.studyId, cmd.collectionEventTypeId, cmd.specimenGroupId)
    ???
  }

  private def addAnnotationToCollectionEventType(study: DisabledStudy,
    cmd: AddAnnotationToCollectionEventTypeCmd,
    listeners: MessageEmitter): DomainValidation[CollectionEventType] = {
    listeners sendEvent AnnotationAddedToCollectionEventTypeEvent(
      cmd.studyId, cmd.collectionEventTypeId, cmd.collectionEventAnnotationTypeId)
    ???
  }

  private def removeAnnotationFromCollectionEventType(study: DisabledStudy,
    cmd: RemoveAnnotationFromCollectionEventTypeCmd,
    listeners: MessageEmitter): DomainValidation[CollectionEventType] = {
    listeners sendEvent AnnotationRemovedFromCollectionEventTypeEvent(
      cmd.studyId, cmd.collectionEventTypeId, cmd.collectionEventAnnotationTypeId)
    ???
  }

}