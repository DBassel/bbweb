package org.biobank.service.participants

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import com.google.inject.ImplementedBy
import javax.inject.{Inject, Named, Singleton}
import org.biobank.domain.participants._
import org.biobank.domain.study._
import org.biobank.infrastructure.command.ParticipantCommands._
import org.biobank.infrastructure.event.ParticipantEvents._
import org.biobank.service.ServiceValidation
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent._
import scala.concurrent.duration._
import scalaz.Scalaz._
import scalaz.Validation.FlatMap._

@ImplementedBy(classOf[ParticipantsServiceImpl])
trait ParticipantsService {

  def get(studyId: StudyId, participantId: ParticipantId): ServiceValidation[Participant]

  def getByUniqueId(studyId: StudyId, uniqueId: String): ServiceValidation[Participant]

  def processCommand(cmd: ParticipantCommand): Future[ServiceValidation[Participant]]

}

@Singleton
class ParticipantsServiceImpl @Inject() (
  @Named("participantsProcessor") val processor: ActorRef,
  val studyRepository:                           StudyRepository,
  val participantRepository:                     ParticipantRepository,
  val collectionEventRepository:                 CollectionEventRepository)
    extends ParticipantsService {

  val log: Logger = LoggerFactory.getLogger(this.getClass)

  implicit val timeout: Timeout = 5.seconds

  def get(studyId: StudyId, participantId: ParticipantId): ServiceValidation[Participant] = {
    participantRepository.withId(studyId, participantId)
  }

  def getByUniqueId(studyId: StudyId, uniqueId: String): ServiceValidation[Participant] = {
    participantRepository.withUniqueId(studyId, uniqueId)
  }

  def processCommand(cmd: ParticipantCommand): Future[ServiceValidation[Participant]] =
    ask(processor, cmd).mapTo[ServiceValidation[ParticipantEvent]].map { validation =>
      for {
        event       <- validation
        participant <- participantRepository.getByKey(ParticipantId(event.id))
      } yield participant
    }

}
