package org.biobank.domain.study

import org.biobank.domain._

import org.slf4j.LoggerFactory

import scalaz._
import Scalaz._

trait CollectionEventTypeRepositoryComponent {

  val collectionEventTypeRepository: CollectionEventTypeRepository

  trait CollectionEventTypeRepository
      extends ReadWriteRepository [CollectionEventTypeId, CollectionEventType] {

    def nextIdentity: CollectionEventTypeId

    def collectionEventTypeWithId(
      studyId: StudyId,
      ceventTypeId: CollectionEventTypeId): DomainValidation[CollectionEventType]

    def allCollectionEventTypesForStudy(studyId: StudyId): Set[CollectionEventType]

    def specimenGroupInUse(studyId: StudyId, specimenGroupId: SpecimenGroupId): Boolean

    def annotationTypeInUse(annotationType: CollectionEventAnnotationType): Boolean

  }
}

trait CollectionEventTypeRepositoryComponentImpl extends CollectionEventTypeRepositoryComponent {

  override val collectionEventTypeRepository: CollectionEventTypeRepository = new CollectionEventTypeRepositoryImpl

  class CollectionEventTypeRepositoryImpl
    extends ReadWriteRepositoryRefImpl[CollectionEventTypeId, CollectionEventType](v => v.id)
    with CollectionEventTypeRepository {

    val log = LoggerFactory.getLogger(this.getClass)

    def nextIdentity: CollectionEventTypeId =
      new CollectionEventTypeId(java.util.UUID.randomUUID.toString.toUpperCase)

    def collectionEventTypeWithId(
      studyId: StudyId,
      ceventTypeId: CollectionEventTypeId): DomainValidation[CollectionEventType] = {
      log.info(s"collectionEventTypeWithId: $this")
      getByKey(ceventTypeId) match {
        case Failure(err) =>
          DomainError(
            s"collection event type does not exist: { studyId: $studyId, ceventTypeId: $ceventTypeId }")
	    .failNel
        case Success(cet) =>
	  log.info(s"collectionEventTypeWithId: $studyId")
          if (cet.studyId.equals(studyId))
            cet.success
          else DomainError(
            "study does not have collection event type:{ studyId: $studyId, ceventTypeId: $ceventTypeId }")
              .failNel
      }
    }

    def allCollectionEventTypesForStudy(studyId: StudyId): Set[CollectionEventType] = {
      getValues.filter(x => x.studyId.equals(studyId)).toSet
    }

    private def nameAvailable(ceventType: CollectionEventType): DomainValidation[Boolean] = {
      val exists = getValues.exists { item =>
        item.studyId.equals(ceventType.studyId) &&
          item.name.equals(ceventType.name) &&
          !item.id.equals(ceventType.id)
      }

      if (exists)
        DomainError("collection event type with name already exists: %s" format ceventType.name).failNel
      else
        true.success
    }

    def specimenGroupInUse(studyId: StudyId, specimenGroupId: SpecimenGroupId): Boolean = {
      val sgId = specimenGroupId.toString
      val studyCeventTypes = getValues.filter(cet => cet.studyId.equals(studyId))
      studyCeventTypes.exists(cet =>
        cet.specimenGroupData.exists(sgd => sgd.specimenGroupId.equals(sgId)))
    }

    def annotationTypeInUse(annotationType: CollectionEventAnnotationType): Boolean = {
      val studyCeventTypes = getValues.filter(cet => cet.studyId.equals(annotationType.studyId))
      studyCeventTypes.exists(cet =>
        cet.annotationTypeData.exists(atd =>
          atd.annotationTypeId.equals(annotationType.id.id)))
    }


    override def put(value: CollectionEventType): CollectionEventType = {
      log.info(s"put: $this -- $value")
      super.put(value)
    }
  }
}
