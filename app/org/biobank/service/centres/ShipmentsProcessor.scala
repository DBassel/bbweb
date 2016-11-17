package org.biobank.service.centres

import akka.actor._
import akka.persistence.{RecoveryCompleted, SnapshotOffer}
import javax.inject.Inject
import org.biobank.TestData
import org.biobank.domain.centre._
import org.biobank.domain.participants.{SpecimenId, SpecimenRepository}
import org.biobank.infrastructure.command.ShipmentCommands._
import org.biobank.infrastructure.command.ShipmentSpecimenCommands._
import org.biobank.infrastructure.event.ShipmentEvents._
import org.biobank.infrastructure.event.ShipmentSpecimenEvents._
import org.biobank.service.{Processor, ServiceError, ServiceValidation}
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import scalaz.Scalaz._
import scalaz.Validation.FlatMap._

object ShipmentsProcessor {

  def props = Props[ShipmentsProcessor]

}

/**
 * Handles commands related to shipments.
 */
class ShipmentsProcessor @Inject() (val shipmentRepository:         ShipmentRepository,
                                    val shipmentSpecimenRepository: ShipmentSpecimenRepository,
                                    val centreRepository:           CentreRepository,
                                    val specimenRepository:         SpecimenRepository,
                                    val testData:                   TestData)
    extends Processor
    with ShipmentValidations
    with ShipmentConstraints {
  import org.biobank.CommonValidations._

  override def persistenceId = "shipments-processor-id"

  case class SnapshotState(shipments: Set[Shipment],
                           shipmentSpecimens: Set[ShipmentSpecimen])

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  val receiveRecover: Receive = {
    case event: ShipmentEvent => event.eventType match {
      case et: ShipmentEvent.EventType.Added                  => applyAddedEvent(event)
      case et: ShipmentEvent.EventType.CourierNameUpdated     => applyCourierNameUpdatedEvent(event)
      case et: ShipmentEvent.EventType.TrackingNumberUpdated  => applyTrackingNumberUpdatedEvent(event)
      case et: ShipmentEvent.EventType.FromLocationUpdated    => applyFromLocationUpdatedEvent(event)
      case et: ShipmentEvent.EventType.ToLocationUpdated      => applyToLocationUpdatedEvent(event)
      case et: ShipmentEvent.EventType.Created                => applyCreatedEvent(event)
      case et: ShipmentEvent.EventType.Packed                 => applyPackedEvent(event)
      case et: ShipmentEvent.EventType.Sent                   => applySentEvent(event)
      case et: ShipmentEvent.EventType.Received               => applyReceivedEvent(event)
      case et: ShipmentEvent.EventType.Unpacked               => applyUnpackedEvent(event)
      case et: ShipmentEvent.EventType.Lost                   => applyLostEvent(event)
      case et: ShipmentEvent.EventType.Removed                => applyRemovedEvent(event)
      case et: ShipmentEvent.EventType.SkippedToSentState     => applySkippedToSentStateEvent(event)
      case et: ShipmentEvent.EventType.SkippedToUnpackedState => applySkippedToUnpackedStateEvent(event)

      case event => log.error(s"event not handled: $event")
    }

    case event: ShipmentSpecimenEvent => event.eventType match {
      case et: ShipmentSpecimenEvent.EventType.Added            => applySpecimenAddedEvent(event)
      case et: ShipmentSpecimenEvent.EventType.Removed          => applySpecimenRemovedEvent(event)
      case et: ShipmentSpecimenEvent.EventType.ContainerUpdated => applySpecimenContainerAddedEvent(event)
      case et: ShipmentSpecimenEvent.EventType.Received         => applySpecimenReceivedEvent(event)
      case et: ShipmentSpecimenEvent.EventType.Missing          => applySpecimenMissingEvent(event)
      case et: ShipmentSpecimenEvent.EventType.Extra            => applySpecimenExtraEvent(event)

      case event => log.error(s"event not handled: $event")
    }


    case SnapshotOffer(_, snapshot: SnapshotState) =>
      snapshot.shipments.foreach{ shipmentRepository.put(_) }
      snapshot.shipmentSpecimens.foreach{ shipmentSpecimenRepository.put(_) }

    case RecoveryCompleted =>
  }

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  val receiveCommand: Receive = {

    case cmd: AddShipmentCmd =>
      process(addCmdToEvent(cmd))(applyAddedEvent)

    case cmd: UpdateShipmentCourierNameCmd     =>
      processUpdateCmdOnCreated(cmd, updateCourierNameCmdToEvent, applyCourierNameUpdatedEvent)

    case cmd: UpdateShipmentTrackingNumberCmd =>
      processUpdateCmdOnCreated(cmd, updateTrackingNumberCmdToEvent, applyTrackingNumberUpdatedEvent)

    case cmd: UpdateShipmentFromLocationCmd =>
      processUpdateCmdOnCreated(cmd, updateFromLocationCmdToEvent, applyFromLocationUpdatedEvent)

    case cmd: UpdateShipmentToLocationCmd =>
      processUpdateCmdOnCreated(cmd, updateToLocationCmdToEvent, applyToLocationUpdatedEvent)

    case cmd: CreatedShipmentCmd =>
      processUpdateCmd(cmd, createdCmdToEvent, applyCreatedEvent)

    case cmd: PackShipmentCmd =>
      processUpdateCmd(cmd, packCmdToEvent, applyPackedEvent)

    case cmd: SendShipmentCmd =>
      processUpdateCmd(cmd, sendCmdToEvent, applySentEvent)

    case cmd: ReceiveShipmentCmd =>
      processUpdateCmd(cmd, receiveCmdToEvent, applyReceivedEvent)

    case cmd: UnpackShipmentCmd =>
      processUpdateCmd(cmd, unpackCmdToEvent, applyUnpackedEvent)

    case cmd: LostShipmentCmd =>
      processUpdateCmd(cmd, lostCmdToEvent, applyLostEvent)

    case cmd: ShipmentSkipStateToSentCmd =>
      processUpdateCmdOnCreated(cmd, skipStateToSentCmdToEvent, applySkippedToSentStateEvent)

    case cmd: ShipmentSkipStateToUnpackedCmd =>
      processUpdateCmd(cmd, skipStateToUnpackedCmdToEvent, applySkippedToUnpackedStateEvent)

    case cmd: ShipmentRemoveCmd =>
      processUpdateCmdOnCreated(cmd, removeCmdToEvent, applyRemovedEvent)

    case cmd: ShipmentSpecimenAddCmd =>
      process(addSpecimenCmdToEvent(cmd))(applySpecimenAddedEvent)

    case cmd: ShipmentSpecimenRemoveCmd =>
      processSpecimenUpdateCmd(cmd, removeSpecimenCmdToEvent, applySpecimenRemovedEvent)

    case cmd: ShipmentSpecimenUpdateContainerCmd =>
      processSpecimenUpdateCmd(cmd, updateSpecimenContainerCmdToEvent, applySpecimenContainerAddedEvent)

    case cmd: ShipmentSpecimenReceivedCmd =>
      processSpecimenUpdateCmd(cmd, specimenReceivedCmdToEvent, applySpecimenReceivedEvent)

    case cmd: ShipmentSpecimenMissingCmd =>
      processSpecimenUpdateCmd(cmd, specimenMissingCmdToEvent, applySpecimenMissingEvent)

    case cmd: ShipmentSpecimenExtraCmd =>
      processSpecimenUpdateCmd(cmd, specimenExtraCmdToEvent, applySpecimenExtraEvent)

    case "snap" =>
      saveSnapshot(SnapshotState(shipmentRepository.getValues.toSet,
                                 shipmentSpecimenRepository.getValues.toSet))
      stash()

    case cmd => log.error(s"shipmentsProcessor: message not handled: $cmd")
  }

  private def addCmdToEvent(cmd: AddShipmentCmd) = {
    for {
      id         <- validNewIdentity(shipmentRepository.nextIdentity, shipmentRepository)
      fromCentre <- centreRepository.getByLocationId(cmd.fromLocationId)
      toCentre   <- centreRepository.getByLocationId(cmd.toLocationId)
      shipment   <- CreatedShipment.create(id             = id,
                                           version        = 0L,
                                           timeAdded      = DateTime.now,
                                           courierName    = cmd.courierName,
                                           trackingNumber = cmd.trackingNumber,
                                           fromCentreId   = fromCentre.id,
                                           fromLocationId = cmd.fromLocationId,
                                           toCentreId     = toCentre.id,
                                           toLocationId   = cmd.toLocationId)
    } yield ShipmentEvent(id.id).update(
      _.userId               := cmd.userId,
      _.time                 := ISODateTimeFormat.dateTime.print(DateTime.now),
      _.added.courierName    := shipment.courierName,
      _.added.trackingNumber := shipment.trackingNumber,
      _.added.fromCentreId   := shipment.fromCentreId.id,
      _.added.fromLocationId := shipment.fromLocationId,
      _.added.toCentreId     := shipment.toCentreId.id,
      _.added.toLocationId   := shipment.toLocationId)
  }

  private def updateCourierNameCmdToEvent(cmd:      UpdateShipmentCourierNameCmd,
                                          shipment: CreatedShipment): ServiceValidation[ShipmentEvent] = {
    shipment.withCourier(cmd.courierName).map { s =>
      ShipmentEvent(shipment.id.id).update(
        _.userId                         := cmd.userId,
        _.time                           := ISODateTimeFormat.dateTime.print(DateTime.now),
        _.courierNameUpdated.version     := cmd.expectedVersion,
        _.courierNameUpdated.courierName := cmd.courierName)
    }
  }

  private def updateTrackingNumberCmdToEvent(cmd: UpdateShipmentTrackingNumberCmd,
                                             shipment: CreatedShipment): ServiceValidation[ShipmentEvent] = {
    shipment.withTrackingNumber(cmd.trackingNumber).map { s =>
      ShipmentEvent(shipment.id.id).update(
        _.userId                               := cmd.userId,
        _.time                                 := ISODateTimeFormat.dateTime.print(DateTime.now),
        _.trackingNumberUpdated.version        := cmd.expectedVersion,
        _.trackingNumberUpdated.trackingNumber := cmd.trackingNumber)
    }
  }

  private def updateFromLocationCmdToEvent(cmd: UpdateShipmentFromLocationCmd,
                                           shipment: CreatedShipment): ServiceValidation[ShipmentEvent] = {
    for {
      centre      <- centreRepository.getByLocationId(cmd.locationId)
      location    <- centre.locationWithId(cmd.locationId)
      newShipment <- shipment.withFromLocation(centre.id, location.uniqueId)
    } yield ShipmentEvent(shipment.id.id).update(
      _.userId                         := cmd.userId,
      _.time                           := ISODateTimeFormat.dateTime.print(DateTime.now),
      _.fromLocationUpdated.version    := cmd.expectedVersion,
      _.fromLocationUpdated.centreId   := centre.id.id,
      _.fromLocationUpdated.locationId := cmd.locationId)
  }

  private def updateToLocationCmdToEvent(cmd: UpdateShipmentToLocationCmd,
                                         shipment: CreatedShipment): ServiceValidation[ShipmentEvent] = {
    for {
      centre      <- centreRepository.getByLocationId(cmd.locationId)
      location    <- centre.locationWithId(cmd.locationId)
      newShipment <- shipment.withToLocation(centre.id, location.uniqueId)
    } yield ShipmentEvent(shipment.id.id).update(
      _.userId                       := cmd.userId,
      _.time                         := ISODateTimeFormat.dateTime.print(DateTime.now),
      _.toLocationUpdated.version    := cmd.expectedVersion,
      _.toLocationUpdated.centreId   := centre.id.id,
      _.toLocationUpdated.locationId := cmd.locationId)
  }

  private def createdCmdToEvent(cmd: CreatedShipmentCmd, shipment: Shipment):
      ServiceValidation[ShipmentEvent] = {
    shipment.isPacked.fold(
      err => InvalidState(s"shipment is not packed: ${shipment.id}").failureNel[ShipmentEvent],
      s => ShipmentEvent(shipment.id.id).update(
        _.userId          := cmd.userId,
        _.time            := ISODateTimeFormat.dateTime.print(DateTime.now),
        _.created.version := cmd.expectedVersion).successNel[String]
    )
  }

  private def packCmdToEvent(cmd: PackShipmentCmd, shipment: Shipment):
      ServiceValidation[ShipmentEvent] = {
    shipment match {
      case _: CreatedShipment | _: SentShipment =>
        ShipmentEvent(shipment.id.id).update(
          _.userId                 := cmd.userId,
          _.time                   := ISODateTimeFormat.dateTime.print(DateTime.now),
          _.packed.version         := cmd.expectedVersion,
          _.packed.stateChangeTime := ISODateTimeFormat.dateTime.print(cmd.datetime)).successNel[String]
      case _ =>
        InvalidState(s"cannot change to packed state: ${shipment.id}").failureNel[ShipmentEvent]
    }
  }

  private def sendCmdToEvent(cmd: SendShipmentCmd, shipment: Shipment):
      ServiceValidation[ShipmentEvent] = {
    val valid = shipment match {
        case ps: PackedShipment   => ps.send(cmd.datetime)
        case rs: ReceivedShipment => rs.backToSent.successNel[String]
        case _ =>
          InvalidState(s"cannot change to sent state: ${shipment.id}").failureNel[Shipment]
      }

    valid.map { s =>
      ShipmentEvent(shipment.id.id).update(
        _.userId               := cmd.userId,
        _.time                 := ISODateTimeFormat.dateTime.print(DateTime.now),
        _.sent.version         := cmd.expectedVersion,
        _.sent.stateChangeTime := ISODateTimeFormat.dateTime.print(cmd.datetime))
    }
  }

  private def receiveCmdToEvent(cmd: ReceiveShipmentCmd, shipment: Shipment):
      ServiceValidation[ShipmentEvent] = {
    val valid = shipment match {
        case ss: SentShipment =>     ss.receive(cmd.datetime)
        case us: UnpackedShipment => us.backToReceived.successNel[String]
        case _ =>
          InvalidState(s"cannot change to received state: ${shipment.id}").failureNel[Shipment]
      }
    valid.map { s =>
      ShipmentEvent(shipment.id.id).update(
        _.userId                   := cmd.userId,
        _.time                     := ISODateTimeFormat.dateTime.print(DateTime.now),
        _.received.version         := cmd.expectedVersion,
        _.received.stateChangeTime := ISODateTimeFormat.dateTime.print(cmd.datetime))
    }
  }

  private def unpackCmdToEvent(cmd: UnpackShipmentCmd, shipment: Shipment):
      ServiceValidation[ShipmentEvent] = {
    val valid = shipment match {
        case rs: ReceivedShipment => rs.unpack(cmd.datetime)
        case _ =>
          InvalidState(s"cannot change to unpacked state: ${shipment.id}").failureNel[Shipment]
      }
    valid.map { s =>
      ShipmentEvent(shipment.id.id).update(
        _.userId                   := cmd.userId,
        _.time                     := ISODateTimeFormat.dateTime.print(DateTime.now),
        _.unpacked.version         := cmd.expectedVersion,
        _.unpacked.stateChangeTime := ISODateTimeFormat.dateTime.print(cmd.datetime))
    }
  }

  private def lostCmdToEvent(cmd: LostShipmentCmd, shipment: Shipment):
      ServiceValidation[ShipmentEvent] = {
    shipment.isSent.fold(
      err => InvalidState(s"cannot change to lost state: ${shipment.id}").failureNel[ShipmentEvent],
      s   => ShipmentEvent(shipment.id.id).update(
        _.userId       := cmd.userId,
        _.time         := ISODateTimeFormat.dateTime.print(DateTime.now),
        _.lost.version := cmd.expectedVersion).successNel[String]
    )
  }

  private def skipStateToSentCmdToEvent(cmd: ShipmentSkipStateToSentCmd,
                                        shipment: CreatedShipment): ServiceValidation[ShipmentEvent] = {
    shipment.skipToSent(cmd.timePacked, cmd.timeSent).map { _ =>
      ShipmentEvent(shipment.id.id).update(
        _.userId                        := cmd.userId,
        _.time                          := ISODateTimeFormat.dateTime.print(DateTime.now),
        _.skippedToSentState.version    := cmd.expectedVersion,
        _.skippedToSentState.timePacked := ISODateTimeFormat.dateTime.print(cmd.timePacked),
        _.skippedToSentState.timeSent   := ISODateTimeFormat.dateTime.print(cmd.timeSent))
    }
  }

  private def skipStateToUnpackedCmdToEvent(cmd: ShipmentSkipStateToUnpackedCmd,
                                            shipment: Shipment): ServiceValidation[ShipmentEvent] = {
    shipment match {
      case s: SentShipment =>
        s.skipToUnpacked(cmd.timeReceived, cmd.timeUnpacked).map { _ =>
          ShipmentEvent(s.id.id).update(
            _.userId                              := cmd.userId,
            _.time                                := ISODateTimeFormat.dateTime.print(DateTime.now),
            _.skippedToUnpackedState.version      := cmd.expectedVersion,
            _.skippedToUnpackedState.timeReceived := ISODateTimeFormat.dateTime.print(cmd.timeReceived),
            _.skippedToUnpackedState.timeUnpacked := ISODateTimeFormat.dateTime.print(cmd.timeUnpacked))
        }
      case _ =>
        InvalidState(s"shipment not sent: ${shipment.state}").failureNel[ShipmentEvent]
    }
  }

  private def removeCmdToEvent(cmd: ShipmentRemoveCmd, shipment: CreatedShipment)
      : ServiceValidation[ShipmentEvent] = {
    val shipmentId = ShipmentId(cmd.id)
    for {
      shipment     <- shipmentRepository.getByKey(shipmentId)
      isCreated    <- shipment.isCreated
      hasSpecimens <- {
        if (shipmentSpecimenRepository.allForShipment(shipmentId).isEmpty) true.successNel[String]
        else ServiceError(s"shipment has specimens, remove specimens first").failureNel[Boolean]
      }
    } yield ShipmentEvent(shipment.id.id).update(
      _.userId          := cmd.userId,
      _.time            := ISODateTimeFormat.dateTime.print(DateTime.now),
      _.removed.version := cmd.expectedVersion)
  }

  private def addSpecimenCmdToEvent(cmd : ShipmentSpecimenAddCmd)
      : ServiceValidation[ShipmentSpecimenEvent] = {
    val shipmentId = ShipmentId(cmd.shipmentId)
    val specimenId = SpecimenId(cmd.specimenId)
    val shipmentContainerId = cmd.shipmentContainerId.map(ShipmentContainerId.apply)

    for {
      specimen   <- specimenRepository.getByKey(specimenId)
      shipment   <- shipmentRepository.getCreated(shipmentId)
      canBeAdded <- specimenNotPresentInShipment(shipmentRepository,
                                                shipmentSpecimenRepository,
                                                specimenId)
      atCentre   <- {
        if (specimen.locationId == shipment.fromLocationId) true.successNel[String]
        else EntityCriteriaError("specimen not present at shipment's originating centre").failureNel[Boolean]
      }
      id         <- validNewIdentity(shipmentSpecimenRepository.nextIdentity, shipmentSpecimenRepository)
      ss         <- ShipmentSpecimen.create(id                  = id,
                                            version             = 0L,
                                            shipmentId          = shipmentId,
                                            specimenId          = specimenId,
                                            state               = ShipmentItemState.Present,
                                            shipmentContainerId = shipmentContainerId)
    } yield ShipmentSpecimenEvent(id.id).update(
        _.userId                            := cmd.userId,
        _.time                              := ISODateTimeFormat.dateTime.print(DateTime.now),
        _.added.shipmentId                  := cmd.shipmentId,
        _.added.specimenId                  := cmd.specimenId,
        _.added.optionalShipmentContainerId := cmd.shipmentContainerId
      )
  }

  private def removeSpecimenCmdToEvent(cmd:              ShipmentSpecimenRemoveCmd,
                                       shipment:         Shipment,
                                       shipmentSpecimen: ShipmentSpecimen)
      : ServiceValidation[ShipmentSpecimenEvent] = {
    val shipmentId = ShipmentId(cmd.shipmentId)
    for {
      shipment   <- shipmentRepository.getCreated(shipmentId)
      isPresent <- shipmentSpecimen.isStatePresent
    } yield ShipmentSpecimenEvent(shipmentSpecimen.id.id).update(
      _.userId          := cmd.userId,
      _.time            := ISODateTimeFormat.dateTime.print(DateTime.now),
      _.removed.version := cmd.expectedVersion)
  }

  private def updateSpecimenContainerCmdToEvent(cmd :             ShipmentSpecimenUpdateContainerCmd,
                                                shipment:         Shipment,
                                                shipmentSpecimen: ShipmentSpecimen)
      : ServiceValidation[ShipmentSpecimenEvent] = {
    val shipmentContainerId = cmd.shipmentContainerId.map(ShipmentContainerId.apply)

    shipmentSpecimen.withShipmentContainer(shipmentContainerId) map { _ =>
      ShipmentSpecimenEvent(cmd.id).update(
        _.userId                                       := cmd.userId,
        _.time                                         := ISODateTimeFormat.dateTime.print(DateTime.now),
        _.containerUpdated.version                     := cmd.expectedVersion,
        _.containerUpdated.optionalShipmentContainerId := cmd.shipmentContainerId)
    }
  }

  private def specimenReceivedCmdToEvent(cmd :             ShipmentSpecimenReceivedCmd,
                                         shipment:         Shipment,
                                         shipmentSpecimen: ShipmentSpecimen)
      : ServiceValidation[ShipmentSpecimenEvent] = {
    for {
      isUnpacked <- {
        shipment match {
          case s: UnpackedShipment => s.successNel[String]
          case s => InvalidState(s"shipment not unpacked: $id").failureNel[UnpackedShipment]
        }
      }
      received   <- shipmentSpecimen.received
    } yield ShipmentSpecimenEvent(cmd.id).update(
      _.userId           := cmd.userId,
      _.time             := ISODateTimeFormat.dateTime.print(DateTime.now),
      _.received.version := cmd.expectedVersion)
  }

  private def specimenMissingCmdToEvent(cmd :             ShipmentSpecimenMissingCmd,
                                        shipment:         Shipment,
                                        shipmentSpecimen: ShipmentSpecimen)
      : ServiceValidation[ShipmentSpecimenEvent] = {
    for {
      unpacked <- shipment.isUnpacked
      missing  <- shipmentSpecimen.missing
    } yield ShipmentSpecimenEvent(cmd.id).update(
      _.userId          := cmd.userId,
      _.time            := ISODateTimeFormat.dateTime.print(DateTime.now),
      _.missing.version := cmd.expectedVersion)
  }

  private def specimenExtraCmdToEvent(cmd :             ShipmentSpecimenExtraCmd,
                                      shipment:         Shipment,
                                      shipmentSpecimen: ShipmentSpecimen)
      : ServiceValidation[ShipmentSpecimenEvent] = {
    for {
      unpacked <- shipment.isUnpacked
      extra    <- shipmentSpecimen.extra
    } yield ShipmentSpecimenEvent(cmd.id).update(
      _.userId        := cmd.userId,
      _.time          := ISODateTimeFormat.dateTime.print(DateTime.now),
      _.extra.version := cmd.expectedVersion)
  }

  private def applyAddedEvent(event: ShipmentEvent) = {
    if (!event.eventType.isAdded) {
      log.error(s"invalid event type: $event")
    } else {
      val addedEvent = event.getAdded
      val eventTime  = ISODateTimeFormat.dateTime.parseDateTime(event.getTime)
      val add = CreatedShipment.create(id             = ShipmentId(event.id),
                                       version        = 0L,
                                       timeAdded      = eventTime,
                                       courierName    = addedEvent.getCourierName,
                                       trackingNumber = addedEvent.getTrackingNumber,
                                       fromCentreId   = CentreId(addedEvent.getFromCentreId),
                                       fromLocationId = addedEvent.getFromLocationId,
                                       toCentreId     = CentreId(addedEvent.getToCentreId),
                                       toLocationId   = addedEvent.getToLocationId)
      add.foreach(shipmentRepository.put)

      if (add.isFailure) {
        log.error(s"could not add shipment from event: $event, err: $add")
      }
    }
  }

  private def applyCourierNameUpdatedEvent(event: ShipmentEvent): Unit = {
    onValidEventAndVersion(event,
                           event.eventType.isCourierNameUpdated,
                           event.getCourierNameUpdated.getVersion) { (shipment, _, time) =>
      val v = for {
          created <- shipment.isCreated
          updated <- created.withCourier(event.getCourierNameUpdated.getCourierName)
        } yield updated.copy(timeModified = Some(time))
      v.foreach(shipmentRepository.put)
      v.map(_ => true)
    }
  }

  private def applyTrackingNumberUpdatedEvent(event: ShipmentEvent): Unit = {
    onValidEventAndVersion(event,
                           event.eventType.isTrackingNumberUpdated,
                           event.getTrackingNumberUpdated.getVersion) { (shipment, _, time) =>
      val v = for {
          created <- shipment.isCreated
          updated <- created.withTrackingNumber(event.getTrackingNumberUpdated.getTrackingNumber)
        } yield updated.copy(timeModified = Some(time))
      v.foreach(shipmentRepository.put)
      v.map(_ => true)
    }
  }

  private def applyFromLocationUpdatedEvent(event: ShipmentEvent): Unit = {
    onValidEventAndVersion(event,
                           event.eventType.isFromLocationUpdated,
                           event.getFromLocationUpdated.getVersion) { (shipment, _, time) =>
      val centreId = CentreId(event.getFromLocationUpdated.getCentreId)
      val locationId = event.getFromLocationUpdated.getLocationId
      val v = for {
          created <- shipment.isCreated
          updated <- created.withFromLocation(centreId, locationId)
        } yield updated.copy(timeModified = Some(time))
      v.foreach(shipmentRepository.put)
      v.map(_ => true)
    }
  }

  private def applyToLocationUpdatedEvent(event: ShipmentEvent): Unit = {
    onValidEventAndVersion(event,
                           event.eventType.isToLocationUpdated,
                           event.getToLocationUpdated.getVersion) { (shipment, _, time) =>
      val centreId = CentreId(event.getToLocationUpdated.getCentreId)
      val locationId = event.getToLocationUpdated.getLocationId
      val v = for {
          created <- shipment.isCreated
          updated <- created.withToLocation(centreId, locationId)
        } yield updated.copy(timeModified = Some(time))
      v.foreach(shipmentRepository.put)
      v.map(_ => true)
    }
  }

  private def applyCreatedEvent(event: ShipmentEvent): Unit = {
    onValidEventAndVersion(event,
                           event.eventType.isCreated,
                           event.getCreated.getVersion) { (shipment, _, time) =>

      shipment.isPacked.map { p =>
        val created = p.created.copy(timeModified = Some(time))
        shipmentRepository.put(created)
        true
      }
    }
  }

  private def applyPackedEvent(event: ShipmentEvent): Unit = {
    onValidEventAndVersion(event,
                           event.eventType.isPacked,
                           event.getPacked.getVersion) { (shipment, _, time) =>
      val stateChangeTime =
        ISODateTimeFormat.dateTime.parseDateTime(event.getPacked.getStateChangeTime)

      val updated = shipment match {
          case created: CreatedShipment =>
            created.pack(stateChangeTime).copy(timeModified = Some(time)).successNel[String]

          case sent: SentShipment =>
            sent.backToPacked.copy(timeModified = Some(time)).successNel[String]

          case _ =>
            InvalidState(s"cannot change to packed state: ${shipment.id}").failureNel[Shipment]
        }

      updated.map { s =>
        shipmentRepository.put(s)
        true
      }
    }
  }

  private def applySentEvent(event: ShipmentEvent): Unit =  {
    onValidEventAndVersion(event,
                           event.eventType.isSent,
                           event.getSent.getVersion) { (shipment, _, time) =>
      val stateChangeTime =
        ISODateTimeFormat.dateTime.parseDateTime(event.getSent.getStateChangeTime)
      val updated = shipment match {
          case packed: PackedShipment =>
            packed.send(stateChangeTime).map(_.copy(timeModified = Some(time)))

          case received: ReceivedShipment =>
            received.backToSent.copy(timeModified = Some(time)).successNel[String]

          case _ =>
            InvalidState(s"cannot change to sent state: ${shipment.id}").failureNel[Shipment]
        }

      updated.map { s =>
        shipmentRepository.put(s)
        true
      }
    }
  }

  private def applyReceivedEvent(event: ShipmentEvent): Unit = {
    onValidEventAndVersion(event,
                           event.eventType.isReceived,
                           event.getReceived.getVersion) { (shipment, _, time) =>
      val stateChangeTime =
        ISODateTimeFormat.dateTime.parseDateTime(event.getReceived.getStateChangeTime)
      val updated = shipment match {
          case sent: SentShipment =>
            sent.receive(stateChangeTime).map(_.copy(timeModified = Some(time)))

          case unpacked: UnpackedShipment =>
            unpacked.backToReceived.copy(timeModified = Some(time)).successNel[String]

          case _ =>
            InvalidState(s"cannot change to received state: ${shipment.id}").failureNel[Shipment]
        }

      updated.map { s =>
        shipmentRepository.put(s)
        true
      }
    }
  }

  private def applyUnpackedEvent(event: ShipmentEvent): Unit = {
    onValidEventAndVersion(event,
                           event.eventType.isUnpacked,
                           event.getUnpacked.getVersion) { (shipment, _, time) =>
      val stateChangeTime =
        ISODateTimeFormat.dateTime.parseDateTime(event.getUnpacked.getStateChangeTime)
      for {
        received <- shipment.isReceived
        unpacked <- received.copy(timeModified = Some(time)).unpack(stateChangeTime)
      } yield {
        shipmentRepository.put(unpacked)
        true
      }
    }
  }

  private def applyLostEvent(event: ShipmentEvent): Unit = {
    onValidEventAndVersion(event,
                           event.eventType.isLost,
                           event.getLost.getVersion) { (shipment, _, time) =>

      shipment.isSent.map { sent =>
        val lost = sent.lost
        shipmentRepository.put(lost)
        true
      }
    }
  }

  private def applySkippedToSentStateEvent(event: ShipmentEvent): Unit = {
    onValidEventAndVersion(event,
                           event.eventType.isSkippedToSentState,
                           event.getSkippedToSentState.getVersion) { (shipment, _, time) =>
      val timePacked = ISODateTimeFormat.dateTime.parseDateTime(event.getSkippedToSentState.getTimePacked)
      val timeSent   = ISODateTimeFormat.dateTime.parseDateTime(event.getSkippedToSentState.getTimeSent)
      val v = for {
          created <- shipment.isCreated
          updated <- created.skipToSent(timePacked, timeSent)
        } yield updated.copy(timeModified = Some(time))
      v.foreach(shipmentRepository.put)
      v.map(_ => true)
    }
  }

  private def applySkippedToUnpackedStateEvent(event: ShipmentEvent): Unit = {
    onValidEventAndVersion(event,
                           event.eventType.isSkippedToUnpackedState,
                           event.getSkippedToUnpackedState.getVersion) { (shipment, _, time) =>
      val timeReceived =
        ISODateTimeFormat.dateTime.parseDateTime(event.getSkippedToUnpackedState.getTimeReceived)
      val timeUnpacked =
        ISODateTimeFormat.dateTime.parseDateTime(event.getSkippedToUnpackedState.getTimeUnpacked)

      val v = for {
          sent     <- shipment.isSent
          updated  <- sent.skipToUnpacked(timeReceived, timeUnpacked)
        } yield updated.copy(timeModified = Some(time))
      v.foreach(shipmentRepository.put)
      v.map(_ => true)
    }
  }

  private def applyRemovedEvent(event: ShipmentEvent): Unit = {
    onValidEventAndVersion(event,
                           event.eventType.isRemoved,
                           event.getRemoved.getVersion) { (shipment, _, time) =>
      shipmentRepository.remove(shipment)
      true.successNel[String]
    }
  }

  private def applySpecimenAddedEvent(event: ShipmentSpecimenEvent): Unit = {
    if (!event.eventType.isAdded) {
      log.error(s"invalid event type: $event")
    } else {
      val addedEvent          = event.getAdded
      val eventTime           = ISODateTimeFormat.dateTime.parseDateTime(event.getTime)
      val shipmentContainerId = addedEvent.shipmentContainerId.map(ShipmentContainerId.apply)

      val add = ShipmentSpecimen.create(id                  = ShipmentSpecimenId(event.id),
                                        version             = 0L,
                                        shipmentId          = ShipmentId(addedEvent.getShipmentId),
                                        specimenId          = SpecimenId(addedEvent.getSpecimenId),
                                        state               = ShipmentItemState.Present,
                                        shipmentContainerId = shipmentContainerId)
      add.foreach { s => shipmentSpecimenRepository.put(s.copy(timeAdded = eventTime)) }
      if (add.isFailure) {
        log.error(s"could not add shipment specimen from event: $event, err: $add")
      }
    }
  }

  private def applySpecimenRemovedEvent(event: ShipmentSpecimenEvent): Unit = {
    onValidSpecimenEventAndVersion(event,
                                   event.eventType.isRemoved,
                                   event.getRemoved.getVersion) { (shipmentSpecimen, _, time) =>
      shipmentSpecimenRepository.remove(shipmentSpecimen)
      true.successNel[String]
    }
  }

  private def applySpecimenContainerAddedEvent(event: ShipmentSpecimenEvent): Unit = {
    onValidSpecimenEventAndVersion(event,
                                   event.eventType.isContainerUpdated,
                                   event.getContainerUpdated.getVersion) { (shipmentSpecimen, _, time) =>
      val shipmentContainerId = event.getContainerUpdated.shipmentContainerId.map(ShipmentContainerId.apply)
      val v = shipmentSpecimen.withShipmentContainer(shipmentContainerId)
      v.foreach { s => shipmentSpecimenRepository.put(s.copy(timeModified = Some(time))) }
      v.map(_ => true)
    }
  }

  private def applySpecimenReceivedEvent(event: ShipmentSpecimenEvent): Unit = {
    onValidSpecimenEventAndVersion(event,
                                   event.eventType.isReceived,
                                   event.getReceived.getVersion) { (shipmentSpecimen, _, time) =>
      val v = shipmentSpecimen.received
      v.foreach { s => shipmentSpecimenRepository.put(s.copy(timeModified = Some(time))) }
      v.map(_ => true)
    }
  }

  private def applySpecimenMissingEvent(event: ShipmentSpecimenEvent): Unit = {
    onValidSpecimenEventAndVersion(event,
                                   event.eventType.isMissing,
                                   event.getMissing.getVersion) { (shipmentSpecimen, _, time) =>
      val v = shipmentSpecimen.missing
      v.foreach { s => shipmentSpecimenRepository.put(s.copy(timeModified = Some(time))) }
      v.map(_ => true)
    }
  }

  private def applySpecimenExtraEvent(event: ShipmentSpecimenEvent): Unit = {
    onValidSpecimenEventAndVersion(event,
                                   event.eventType.isExtra,
                                   event.getExtra.getVersion) { (shipmentSpecimen, _, time) =>
      val v = shipmentSpecimen.extra
      v.foreach { s => shipmentSpecimenRepository.put(s.copy(timeModified = Some(time))) }
      v.map(_ => true)
    }
  }

  def processUpdateCmd[T <: ShipmentModifyCommand]
    (cmd: T,
     cmdToEvent: (T, Shipment) => ServiceValidation[ShipmentEvent],
     applyEvent: ShipmentEvent => Unit): Unit = {
    val event = for {
        shipment     <- shipmentRepository.getByKey(ShipmentId(cmd.id))
        validVersion <- shipment.requireVersion(cmd.expectedVersion)
        event        <- cmdToEvent(cmd, shipment)
      } yield event
    process(event)(applyEvent)
  }

  def processUpdateCmdOnCreated[T <: ShipmentModifyCommand]
    (cmd: T,
     cmdToEvent: (T, CreatedShipment) => ServiceValidation[ShipmentEvent],
     applyEvent: ShipmentEvent => Unit): Unit = {

    def internal(cmd: T, shipment: Shipment): ServiceValidation[ShipmentEvent] =
      shipment match {
        case s: CreatedShipment => cmdToEvent(cmd, s)
        case s => InvalidState(s"shipment not created: ${shipment.id}").failureNel[ShipmentEvent]
      }

    processUpdateCmd(cmd, internal, applyEvent)
  }

  def processSpecimenUpdateCmd[T <: ShipmentSpecimenModifyCommand]
    (cmd: T,
     cmdToEvent: (T, Shipment, ShipmentSpecimen) => ServiceValidation[ShipmentSpecimenEvent],
     applyEvent: ShipmentSpecimenEvent => Unit): Unit = {
    val event = for {
        shipmentSpecimen <- shipmentSpecimenRepository.getByKey(ShipmentSpecimenId(cmd.id))
        shipment         <- shipmentRepository.getByKey(shipmentSpecimen.shipmentId)
        validVersion     <- shipmentSpecimen.requireVersion(cmd.expectedVersion)
        event            <- cmdToEvent(cmd, shipment, shipmentSpecimen)
      } yield event
    process(event)(applyEvent)
  }

  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  private def onValidEventAndVersion(event:        ShipmentEvent,
                                     eventType:    Boolean,
                                     eventVersion: Long)
                                    (applyEvent:  (Shipment,
                                                   ShipmentEvent,
                                                   DateTime) => ServiceValidation[Boolean])
      : Unit = {
    if (!eventType) {
      log.error(s"invalid event type: $event")
    } else {
      shipmentRepository.getByKey(ShipmentId(event.id)).fold(
        err => log.error(s"shipment from event does not exist: $err"),
        shipment => {
          if (shipment.version != eventVersion) {
            log.error(s"event version check failed: shipment version: ${shipment.version}, event: $event")
          } else {
            val eventTime = ISODateTimeFormat.dateTime.parseDateTime(event.getTime)
            val update = applyEvent(shipment, event, eventTime)
            if (update.isFailure) {
              log.error(s"shipment update from event failed: event: $event, reason: $update")
            }
          }
        }
      )
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  private def onValidSpecimenEventAndVersion(event:        ShipmentSpecimenEvent,
                                             eventType:    Boolean,
                                             eventVersion: Long)
                                            (applyEvent:  (ShipmentSpecimen,
                                                           ShipmentSpecimenEvent,
                                                           DateTime) => ServiceValidation[Boolean])
      : Unit = {
    if (!eventType) {
      log.error(s"invalid event type: $event")
    } else {
      shipmentSpecimenRepository.getByKey(ShipmentSpecimenId(event.id)).fold(
        err => log.error(s"shipmentSpecimen from event does not exist: $err"),
        shipmentSpecimen => {
          if (shipmentSpecimen.version != eventVersion) {
            log.error(s"event version check failed: shipment specimen version: ${shipmentSpecimen.version}, event: $event")
          } else {
            val eventTime = ISODateTimeFormat.dateTime.parseDateTime(event.getTime)
            val update = applyEvent(shipmentSpecimen, event, eventTime)

            if (update.isFailure) {
              log.error(s"shipment specimen update from event failed: $update")
            }
          }
        }
      )
    }
  }

}
