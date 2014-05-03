package org.biobank.service

import org.biobank.fixture._
import org.biobank.domain._
import org.biobank.domain.study._
import org.biobank.infrastructure.command.StudyCommands._
import org.biobank.infrastructure.event.StudyEvents._

import org.slf4j.LoggerFactory
import akka.pattern.ask
import org.scalatest.Tag
import org.scalatest.BeforeAndAfterEach
import scalaz._
import scalaz.Scalaz._

class SpecimenLinkAnnotationTypeProcessorSpec extends StudyProcessorFixture {

  private val log = LoggerFactory.getLogger(this.getClass)

  val nameGenerator = new NameGenerator(this.getClass)

  var disabledStudy: DisabledStudy = null

  // create the study to be used for each tests*
  override def beforeEach: Unit = {
    disabledStudy = factory.createDisabledStudy
    studyRepository.put(disabledStudy)
  }

  "A study processor" can {

    "add a specimen link annotation type" in {
      val annotType = factory.createSpecimenLinkAnnotationType

      val cmd = AddSpecimenLinkAnnotationTypeCmd(
	annotType.studyId.id, annotType.name, annotType.description, annotType.valueType,
	annotType.maxValueCount, annotType.options)
      val validation = ask(studyProcessor, cmd)
        .mapTo[DomainValidation[SpecimenLinkAnnotationTypeAddedEvent]]
        .futureValue

      validation should be('success)
      validation map { event =>
        event shouldBe a[SpecimenLinkAnnotationTypeAddedEvent]
        event should have(
	  'studyId (annotType.studyId.id),
          'name (annotType.name),
          'description (annotType.description),
          'valueType (annotType.valueType),
	  'maxValueCount (annotType.maxValueCount)
	)

	val options = event.options map { eventOptions =>
	  val annotTypeOptions = annotType.options | fail
	  eventOptions should have size annotTypeOptions.size
	  // verify each option
	  annotTypeOptions.map { item =>
	    eventOptions should contain (item)
	  }
	}

        val at = specimenLinkAnnotationTypeRepository.withId(
          disabledStudy.id, AnnotationTypeId(event.annotationTypeId)) | fail
        at.version should be(0)
        specimenLinkAnnotationTypeRepository.allForStudy(disabledStudy.id) should have size 1
      }
    }

    "not add a specimen link annotation type if the name already exists" in {
      val annotType = factory.createSpecimenLinkAnnotationType
      specimenLinkAnnotationTypeRepository.put(annotType)

      val cmd = AddSpecimenLinkAnnotationTypeCmd(
	annotType.studyId.id, annotType.name, annotType.description, annotType.valueType,
	annotType.maxValueCount, annotType.options)
      val validation = ask(studyProcessor, cmd)
        .mapTo[DomainValidation[SpecimenLinkAnnotationTypeAddedEvent]]
        .futureValue

      validation should be('failure)
      validation.swap map { err =>
        err.list should have length 1
        err.list.head should include("name already exists")
      }
    }

    "update a specimen link annotation type" in {
      val annotType = factory.createSpecimenLinkAnnotationType
      specimenLinkAnnotationTypeRepository.put(annotType)

      val annotType2 = factory.createSpecimenLinkAnnotationType

      val cmd = UpdateSpecimenLinkAnnotationTypeCmd(
	annotType.studyId.id, annotType.id.id, annotType.versionOption, annotType2.name,
	annotType2.description, annotType2.valueType, annotType2.maxValueCount, annotType2.options)
      val validation = ask(studyProcessor, cmd)
        .mapTo[DomainValidation[SpecimenLinkAnnotationTypeUpdatedEvent]]
        .futureValue

      validation should be('success)
      validation map { event =>
        event shouldBe a[SpecimenLinkAnnotationTypeUpdatedEvent]
        event should have(
	  'studyId (annotType.studyId.id),
	  'version (annotType.version + 1),
          'name (annotType2.name),
          'description (annotType2.description),
          'valueType (annotType2.valueType),
	  'maxValueCount (annotType2.maxValueCount)
	)

	val options = event.options map { eventOptions =>
	  val annotTypeOptions = annotType2.options | fail
	  eventOptions should have size annotTypeOptions.size
	  // verify each option
	  annotTypeOptions.map { item =>
	    eventOptions should contain (item)
	  }
	}

        val at = specimenLinkAnnotationTypeRepository.withId(
          disabledStudy.id, AnnotationTypeId(event.annotationTypeId)) | fail
        at.version should be(1)
        specimenLinkAnnotationTypeRepository.allForStudy(disabledStudy.id) should have size 1
      }
    }

    "not update a specimen link annotation type to name that already exists" in {
      val annotType = factory.createSpecimenLinkAnnotationType
      specimenLinkAnnotationTypeRepository.put(annotType)

      val annotType2 = factory.createSpecimenLinkAnnotationType
      specimenLinkAnnotationTypeRepository.put(annotType2)

      val dupliacteName = annotType.name

      val cmd = UpdateSpecimenLinkAnnotationTypeCmd(
	annotType2.studyId.id, annotType2.id.id, annotType2.versionOption, dupliacteName,
	annotType2.description, annotType2.valueType, annotType2.maxValueCount, annotType2.options)
      val validation = ask(studyProcessor, cmd)
        .mapTo[DomainValidation[SpecimenLinkAnnotationTypeUpdatedEvent]]
        .futureValue

      validation should be('failure)
      validation.swap map { err =>
        err.list should have length 1
        err.list.head should include("name already exists")
      }
    }

    "not update a specimen link annotation type to the wrong study" in {
      val annotType = factory.createSpecimenLinkAnnotationType
      specimenLinkAnnotationTypeRepository.put(annotType)

      val study2 = factory.createDisabledStudy
      studyRepository.put(study2)

      val cmd = UpdateSpecimenLinkAnnotationTypeCmd(
	study2.id.id, annotType.id.id, annotType.versionOption, annotType.name,
	annotType.description, annotType.valueType, annotType.maxValueCount, annotType.options)
      val validation = ask(studyProcessor, cmd)
        .mapTo[DomainValidation[SpecimenLinkAnnotationTypeUpdatedEvent]]
        .futureValue

      validation should be('failure)
      validation.swap map { err =>
        err.list should have length 1
        err.list.head should include("study does not have annotation type") }
    }

    "not update a specimen link annotation type with an invalid version" in {
      val annotType = factory.createSpecimenLinkAnnotationType
      specimenLinkAnnotationTypeRepository.put(annotType)

      val cmd = UpdateSpecimenLinkAnnotationTypeCmd(
	annotType.studyId.id, annotType.id.id, Some(annotType.version - 1), annotType.name,
	annotType.description, annotType.valueType, annotType.maxValueCount, annotType.options)
      val validation = ask(studyProcessor, cmd)
        .mapTo[DomainValidation[SpecimenLinkAnnotationTypeUpdatedEvent]]
        .futureValue

      validation should be('failure)
      validation.swap map { err =>
        err.list should have length 1
        err.list.head should include("doesn't match current version")
      }
    }

    "remove a specimen link annotation type" in {
      val annotType = factory.createSpecimenLinkAnnotationType
      specimenLinkAnnotationTypeRepository.put(annotType)

      val cmd = RemoveSpecimenLinkAnnotationTypeCmd(
	annotType.studyId.id, annotType.id.id, annotType.versionOption)
      val validation = ask(studyProcessor, cmd)
        .mapTo[DomainValidation[SpecimenLinkAnnotationTypeRemovedEvent]]
        .futureValue

      validation should be('success)
      validation map { event => event shouldBe a[SpecimenLinkAnnotationTypeRemovedEvent] }
    }

    "not remove a specimen link annotation type with invalid version" in {
      val annotType = factory.createSpecimenLinkAnnotationType
      specimenLinkAnnotationTypeRepository.put(annotType)

      val cmd = RemoveSpecimenLinkAnnotationTypeCmd(
	annotType.studyId.id, annotType.id.id, Some(annotType.version - 1))
      val validation = ask(studyProcessor, cmd)
        .mapTo[DomainValidation[SpecimenLinkAnnotationTypeRemovedEvent]]
        .futureValue

      validation should be('failure)
      validation.swap map { err =>
        err.list should have length 1
        err.list.head should include("version mismatch")
      }
    }

  }

}