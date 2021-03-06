package org.biobank.modules

import com.google.inject.AbstractModule
import org.biobank.TestData
import org.biobank.service.centres._
import org.biobank.service.participants._
import org.biobank.service.studies._
import org.biobank.service.users._
import play.api.libs.concurrent.AkkaGuiceSupport

class AkkaModule extends AbstractModule with AkkaGuiceSupport {
  @SuppressWarnings(Array("org.wartremover.warts.Overloading", "org.wartremover.warts.PublicInference"))
  def configure() = {

    bindActor[CentresProcessor]("centresProcessor")
    bindActor[ShipmentsProcessor]("shipmentsProcessor")
    bindActor[UsersProcessor]("usersProcessor")

    bindActor[ParticipantsProcessor]("participantsProcessor")
    bindActor[CollectionEventsProcessor]("collectionEventsProcessor")
    bindActor[SpecimensProcessor]("specimensProcessor")

    bindActor[StudiesProcessor]("studiesProcessor")
    bindActor[CollectionEventTypeProcessor]("collectionEventType")
    bindActor[ProcessingTypeProcessor]("processingType")
    bindActor[SpecimenLinkTypeProcessor]("specimenLinkType")

    bind(classOf[TestData]).asEagerSingleton
  }

}
