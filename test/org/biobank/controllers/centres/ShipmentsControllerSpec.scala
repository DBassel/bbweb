 package org.biobank.controllers.centres

import com.github.nscala_time.time.Imports._
import org.biobank.controllers.PagedResultsSpec
import org.biobank.domain.{EntityState, LocationId}
import org.biobank.domain.centre._
import org.joda.time.DateTime
import org.scalatest.prop.TableDrivenPropertyChecks._
import play.api.libs.json._
import play.api.test.Helpers._
import scala.language.reflectiveCalls

/**
 * Tests the REST API for [[Shipment]]s.
 *
 * Tests for [[ShipmentSpecimen]]s in ShipmentSpecimensControllerSpec.scala.
 */
class ShipmentsControllerSpec
    extends ShipmentsControllerSpecFixtures
    with ShipmentsControllerSpecUtils {
  import org.biobank.TestUtils._
  import org.biobank.infrastructure.JsonUtils._

  val states = Table("state",
                     Shipment.createdState,
                     Shipment.packedState,
                     Shipment.sentState,
                     Shipment.receivedState,
                     Shipment.unpackedState,
                     Shipment.lostState)

  def skipStateCommon(shipment:   Shipment,
                      uriPath:    String,
                      updateJson: JsValue) = {

    shipmentRepository.put(shipment)
    val json = makeRequest(POST, uri(shipment, uriPath), updateJson)

    (json \ "status").as[String] must include ("success")

    shipmentRepository.getByKey(shipment.id) mustSucceed { repoShipment =>
      compareObj((json \ "data").as[JsObject], repoShipment)

      repoShipment must have (
        'id             (shipment.id),
        'version        (shipment.version + 1),
        'courierName    (shipment.courierName),
        'trackingNumber (shipment.trackingNumber),
        'fromLocationId (shipment.fromLocationId.id),
        'toLocationId   (shipment.toLocationId.id))

      checkTimeStamps(repoShipment, shipment.timeAdded, DateTime.now)
    }
  }

  "Shipment REST API" when {

    "GET /shipments/list/:centreId" must {

      "list none" in {
        val centre = factory.createEnabledCentre
        PagedResultsSpec(this).emptyResults(listUri(centre))
      }

      "list a shipment" in {
        val f = createdShipmentFixture
        shipmentRepository.put(f.shipment)
        val jsonItem = PagedResultsSpec(this).singleItemResult(listUri(f.fromCentre))
        compareObj(jsonItem, f.shipment)
      }

      "list multiple shipments" in {
        val f = createdShipmentFixture(2)
        f.shipmentMap.values.foreach(shipmentRepository.put)

        val jsonItems = PagedResultsSpec(this).multipleItemsResult(
            uri       = listUri(f.fromCentre),
            offset    = 0,
            total     = f.shipmentMap.size.toLong,
            maybeNext = None,
            maybePrev = None)
        jsonItems must have size f.shipmentMap.size.toLong

        compareObjs(jsonItems, f.shipmentMap)
      }

      "list a single shipment when filtered by state" in {
        val f = allShipmentsFixture
        f.shipments.values.foreach(shipmentRepository.put)

        forAll(states) { state =>
          info(s"$state shipment")
          val jsonItem = PagedResultsSpec(this)
            .singleItemResult(listUri(f.fromCentre), Map("filter" -> s"state::$state"))
          compareObj(jsonItem, f.shipments.get(state).value)
        }
      }

      "list multiple shipments when filtered by states" in {
        val shipmentStates = List(Shipment.createdState, Shipment.unpackedState)
        val f = allShipmentsFixture
        f.shipments.values.foreach(shipmentRepository.put)

        val jsonItems = PagedResultsSpec(this).multipleItemsResult(
            uri         = listUri(f.fromCentre),
            queryParams = Map("filter" -> s"""state:in:(${shipmentStates.mkString(",")})""",
                              "sort"   -> "state"),
            offset      = 0,
            total       = shipmentStates.size.toLong,
            maybeNext   = None,
            maybePrev   = None)

        jsonItems must have size shipmentStates.size.toLong
        compareObj(jsonItems(0), f.shipments(Shipment.createdState))
        compareObj(jsonItems(1), f.shipments(Shipment.unpackedState))
      }

      "fail when using an invalid filter string" in {
        val f = centresFixture
        val invalidFilterString = "xxx"
        val reply = makeRequest(GET, listUri(f.fromCentre) + s"?filter=$invalidFilterString", BAD_REQUEST)

        (reply \ "status").as[String] must include ("error")

        (reply \ "message").as[String] must include regex ("could not parse filter expression:")
      }

      "fail when using an invalid state filter" in {
        val f = centresFixture
        val invalidStateName = nameGenerator.next[Shipment]

        val reply = makeRequest(GET,
                                listUri(f.fromCentre) + s"?filter=state::$invalidStateName",
                                NOT_FOUND)

        (reply \ "status").as[String] must include ("error")

        (reply \ "message").as[String] must include regex ("shipment state does not exist:")
      }

      "list a single shipment when filtered by courier name" in {
        val f = createdShipmentFixture(2)
        f.shipmentMap.values.foreach(shipmentRepository.put)
        val shipment = f.shipmentMap.values.head
        val uri = s"courierName::${shipment.courierName}"
        val jsonItem = PagedResultsSpec(this)
          .singleItemResult(listUri(f.fromCentre), Map("filter" -> uri))
        compareObj(jsonItem, shipment)
      }

      "list a single shipment when using a 'like' filter on courier name" in {
        val f = createdShipmentFixture(2)
        val shipments = f.shipmentMap.values.toList

        val shipment = shipments(0).copy(courierName = "ABC")
        shipmentRepository.put(shipment)
        shipmentRepository.put(shipments(1).copy(courierName = "DEF"))

        val uri = s"courierName:like:b"
        val jsonItem = PagedResultsSpec(this)
          .singleItemResult(listUri(f.fromCentre), Map("filter" -> uri))
        compareObj(jsonItem, shipment)
      }

      "list multiple shipments when filtered by courier name" in {
        val numShipments = 2
        val f = createdShipmentFixture(numShipments)
        val shipments = f.shipmentMap.values.toList
        val courierNames = shipments.map(_.courierName)

        shipments.foreach(shipmentRepository.put)
        val uri = s"""courierName:in:(${courierNames.mkString(",")})"""

        val jsonItems =
          PagedResultsSpec(this).multipleItemsResult(uri         = listUri(f.fromCentre),
                                                     queryParams = Map("filter" -> uri),
                                                     offset      = 0,
                                                     total       = numShipments.toLong,
                                                     maybeNext   = None,
                                                     maybePrev   = None)
        jsonItems must have size numShipments.toLong
        compareObj(jsonItems(0), shipments(0))
        compareObj(jsonItems(1), shipments(1))
      }

      "list a single shipment when filtered by tracking number" in {
        val f = createdShipmentFixture(2)
        f.shipmentMap.values.foreach(shipmentRepository.put)
        val shipment = f.shipmentMap.values.head
        val jsonItem = PagedResultsSpec(this).singleItemResult(
            listUri(f.fromCentre),
            Map("filter" -> s"trackingNumber::${shipment.trackingNumber}"))
        compareObj(jsonItem, shipment)
      }

      "list a single shipment when filtered with a logical expression" in {
        val numShipments = 2
        val f = createdShipmentFixture(numShipments)
        val shipments = f.shipmentMap.values.toList
        val shipment = shipments(0)

        val expressions = Table(
            "expression",
            s"""courierName::${shipment.courierName};trackingNumber::${shipment.trackingNumber}""",
            s"""courierName::${shipment.courierName},trackingNumber::${shipment.trackingNumber}"""
          )

        forAll(expressions) { expression =>
        shipments.foreach(shipmentRepository.put)
          val jsonItem =
            PagedResultsSpec(this).singleItemResult(uri         = listUri(f.fromCentre),
                                                    queryParams = Map("filter" -> expression))
          compareObj(jsonItem, shipment)
        }
      }

      "list shipments sorted by courier name" in {
        val f = centresFixture
        val shipments = List("FedEx", "UPS", "Canada Post").map { name =>
            factory.createShipment(f.fromCentre, f.toCentre).copy(courierName = name)
          }.toList
        shipments.foreach(shipmentRepository.put)

        val sortExprs = Table("sort by", "courierName", "-courierName")
        forAll(sortExprs) { sortExpr =>
          val jsonItems = PagedResultsSpec(this)
            .multipleItemsResult(uri       = listUri(f.fromCentre),
                                 queryParams = Map("sort" -> sortExpr),
                                 offset    = 0,
                                 total     = shipments.size.toLong,
                                 maybeNext = None,
                                 maybePrev = None)

          jsonItems must have size shipments.size.toLong
          if (sortExpr == sortExprs(0)) {
            compareObj(jsonItems(0), shipments(2))
            compareObj(jsonItems(1), shipments(0))
            compareObj(jsonItems(2), shipments(1))
          } else {
            compareObj(jsonItems(0), shipments(1))
            compareObj(jsonItems(1), shipments(0))
            compareObj(jsonItems(2), shipments(2))
          }
        }
      }

      "list shipments sorted by tracking number" in {
        val f = centresFixture
        val shipments = List("TN2", "TN3", "TN1")
          .map { trackingNumber =>
            factory.createShipment(f.fromCentre, f.toCentre).copy(trackingNumber = trackingNumber)
          }.toList
        shipments.foreach(shipmentRepository.put)

        val sortExprs = Table("sort by", "trackingNumber", "-trackingNumber")
        forAll(sortExprs) { sortExpr =>
          val jsonItems = PagedResultsSpec(this)
            .multipleItemsResult(uri       = listUri(f.fromCentre),
                                 queryParams = Map("sort" -> sortExpr),
                                 offset    = 0,
                                 total     = shipments.size.toLong,
                                 maybeNext = None,
                                 maybePrev = None)
          jsonItems must have size shipments.size.toLong
          if (sortExpr == sortExprs(0)) {
            compareObj(jsonItems(0), shipments(2))
            compareObj(jsonItems(1), shipments(0))
            compareObj(jsonItems(2), shipments(1))
          } else {
            compareObj(jsonItems(0), shipments(1))
            compareObj(jsonItems(1), shipments(0))
            compareObj(jsonItems(2), shipments(2))
          }
        }
      }

      "list a single shipment when using paged query" in {
        val f = centresFixture
        val shipments = List("FedEx", "UPS", "Canada Post")
          .map { name =>
            factory.createShipment(f.fromCentre, f.toCentre).copy(courierName = name)
          }.toList
        shipments.foreach(shipmentRepository.put)
        val jsonItem = PagedResultsSpec(this)
          .singleItemResult(uri       = listUri(f.fromCentre),
                            queryParams = Map("sort" -> "courierName", "limit" -> "1"),
                            total     = shipments.size.toLong,
                            maybeNext = Some(2))
        compareObj(jsonItem, shipments(2))
      }

      "list the last shipment when using paged query" in {
        val f = centresFixture
        val shipments = List("FedEx", "UPS", "Canada Post")
          .map { name =>
            factory.createShipment(f.fromCentre, f.toCentre).copy(courierName = name)
          }.toList
        shipments.foreach(shipmentRepository.put)
        val jsonItem = PagedResultsSpec(this)
          .singleItemResult(uri       = listUri(f.fromCentre),
                            queryParams = Map("sort" -> "courierName", "page" -> "3", "limit" -> "1"),
                            total     = shipments.size.toLong,
                            offset    = 2,
                            maybeNext = None,
                            maybePrev = Some(2))
        compareObj(jsonItem, shipments(1))
      }

      "list a single shipment when using a 'like' filter on tracking number" in {
        val f = createdShipmentFixture(2)
        val shipments = f.shipmentMap.values.toList

        val shipment = shipments(0).copy(trackingNumber = "ABC")
        shipmentRepository.put(shipment)
        shipmentRepository.put(shipments(1).copy(trackingNumber = "DEF"))

        val uri = s"trackingNumber:like:b"
        val jsonItem = PagedResultsSpec(this)
          .singleItemResult(listUri(f.fromCentre), Map("filter" -> uri))
        compareObj(jsonItem, shipment)
      }

      "fail when using an invalid query parameters" in {
        val f = centresFixture
        val url = listUri(f.fromCentre)
        PagedResultsSpec(this).failWithNegativePageNumber(url)
        PagedResultsSpec(this).failWithInvalidPageNumber(url)
        PagedResultsSpec(this).failWithNegativePageSize(url)
        PagedResultsSpec(this).failWithInvalidPageSize(url, 100);
        PagedResultsSpec(this).failWithInvalidSort(url)
      }

    }

    "GET /shipments/:id" must {

      "get a shipment" in {
        val f = createdShipmentFixture
        shipmentRepository.put(f.shipment)
        val json = makeRequest(GET, uri(f.shipment))
                              (json \ "status").as[String] must include ("success")
        val jsonObj = (json \ "data").as[JsObject]
        compareObj(jsonObj, f.shipment)
      }

      "returns an error for an invalid shipment ID" in {
        val shipment = factory.createShipment

        val json = makeRequest(GET, uri(shipment), NOT_FOUND)

        (json \ "status").as[String] must include ("error")

        (json \ "message").as[String] must include regex ("IdNotFound.*shipment")
      }
    }

    "POST /centres" must {

      def shipmentToAddJson(shipment: Shipment) =
        Json.obj("courierName"    -> shipment.courierName,
                 "trackingNumber" -> shipment.trackingNumber,
                 "fromLocationId" -> shipment.fromLocationId.id,
                 "toLocationId"   -> shipment.toLocationId.id,
                 "timePacked"     -> shipment.timePacked)

      "add a shipment" in {
        val f = createdShipmentFixture
        shipmentRepository.put(f.shipment)
        val json = makeRequest(POST, uri, shipmentToAddJson(f.shipment))

        (json \ "status").as[String] must include ("success")

        val jsonId = (json \ "data" \ "id").as[String]
        val shipmentId = ShipmentId(jsonId)
        jsonId.length must be > 0

        shipmentRepository.getByKey(shipmentId) mustSucceed { repoShipment =>
          repoShipment mustBe a[CreatedShipment]
          compareObj((json \ "data").as[JsObject], repoShipment)

          repoShipment must have (
            'id             (shipmentId),
            'version        (0L),
            'courierName    (f.shipment.courierName),
            'trackingNumber (f.shipment.trackingNumber),
            'fromLocationId (f.shipment.fromLocationId.id),
            'toLocationId   (f.shipment.toLocationId.id))

          checkTimeStamps(repoShipment, DateTime.now, None)
          compareTimestamps(repoShipment, f.shipment)
        }
      }

      "fail when adding a shipment with no courier name" in {
        val shipment = createdShipmentFixture.shipment.copy(courierName = "")
        val json = makeRequest(POST, uri, BAD_REQUEST, shipmentToAddJson(shipment))

        (json \ "status").as[String] must include ("error")

        (json \ "message").as[String] must include ("CourierNameInvalid")
      }

      "fail when adding a shipment with no tracking number" in {
        val shipment = createdShipmentFixture.shipment.copy(trackingNumber = "")
        val json = makeRequest(POST, uri, BAD_REQUEST, shipmentToAddJson(shipment))

        (json \ "status").as[String] must include ("error")

        (json \ "message").as[String] must include ("TrackingNumberInvalid")
      }

      "fail when adding a shipment with no FROM location id" in {
        val shipment = createdShipmentFixture.shipment.copy(fromLocationId = LocationId(""))
        val json = makeRequest(POST, uri, NOT_FOUND, shipmentToAddJson(shipment))

        (json \ "status").as[String] must include ("error")

        (json \ "message").as[String] must include regex ("EntityCriteriaError.*centre with location id")
      }

      "fail when adding a shipment with no TO location id" in {
        val shipment = createdShipmentFixture.shipment.copy(toLocationId = LocationId(""))
        val json = makeRequest(POST, uri, NOT_FOUND, shipmentToAddJson(shipment))

        (json \ "status").as[String] must include ("error")

        (json \ "message").as[String] must include regex ("EntityCriteriaError.*centre with location id")
      }

    }


    "POST /shipments/courier/:id" must {

      "allow updating the courier name" in {
        val f = createdShipmentFixture
        val newCourier = nameGenerator.next[Shipment]
        shipmentRepository.put(f.shipment)
        val updateJson = Json.obj("expectedVersion" -> f.shipment.version,
                                  "courierName"     -> newCourier)
        val json = makeRequest(POST, uri(f.shipment, "courier"), updateJson)
                              (json \ "status").as[String] must include ("success")

        shipmentRepository.getByKey(f.shipment.id) mustSucceed { repoShipment =>
          repoShipment mustBe a[CreatedShipment]
          compareObj((json \ "data").as[JsObject], repoShipment)

          repoShipment must have (
            'id             (f.shipment.id),
            'version        (f.shipment.version + 1),
            'courierName    (newCourier),
            'trackingNumber (f.shipment.trackingNumber),
            'fromLocationId (f.shipment.fromLocationId.id),
            'toLocationId   (f.shipment.toLocationId.id))

          checkTimeStamps(repoShipment, f.shipment.timeAdded, DateTime.now)
          compareTimestamps(repoShipment, f.shipment)
        }
      }

      "not allow updating the courier name to an empty string" in {
        val f = createdShipmentFixture
        shipmentRepository.put(f.shipment)
        val updateJson = Json.obj("expectedVersion" -> f.shipment.version,
                                  "courierName"     -> "")
        val json = makeRequest(POST, uri(f.shipment, "courier"), BAD_REQUEST, updateJson)

        (json \ "status").as[String] must include ("error")

        (json \ "message").as[String] must include ("CourierNameInvalid")
      }

      "must not allow updating the courier name on a shipment not in created state"  in {
        val f = allShipmentsFixture

        nonCreatedStates.foreach { state =>
          val shipment = f.shipments(state)
          shipmentRepository.put(shipment)
          val updateJson = Json.obj("expectedVersion" -> shipment.version,
                                    "courierName"     -> nameGenerator.next[String])

          val json = makeRequest(POST, uri(shipment, "courier"), BAD_REQUEST, updateJson)

          (json \ "status").as[String] must include ("error")

          (json \ "message").as[String] must include regex ("InvalidState: shipment not created")
        }
      }

    }

    "POST /shipments/trackingnumber/:id" must {

      "allow updating the tracking number" in {
        val f = createdShipmentFixture
        val newTrackingNumber = nameGenerator.next[Shipment]
        shipmentRepository.put(f.shipment)
        val updateJson = Json.obj("expectedVersion" -> f.shipment.version,
                                  "trackingNumber"  -> newTrackingNumber)
        val json = makeRequest(POST, uri(f.shipment, "trackingnumber"), updateJson)
                              (json \ "status").as[String] must include ("success")

        shipmentRepository.getByKey(f.shipment.id) mustSucceed { repoShipment =>
          repoShipment mustBe a[CreatedShipment]
          compareObj((json \ "data").as[JsObject], repoShipment)

          repoShipment must have (
            'id             (f.shipment.id),
            'version        (f.shipment.version + 1),
            'courierName    (f.shipment.courierName),
            'trackingNumber (newTrackingNumber),
            'fromLocationId (f.shipment.fromLocationId.id),
            'toLocationId   (f.shipment.toLocationId.id))

          checkTimeStamps(repoShipment, f.shipment.timeAdded, DateTime.now)
          compareTimestamps(repoShipment, f.shipment)
        }
      }

      "not allow updating the tracking number to an empty string" in {
        val f = createdShipmentFixture
        shipmentRepository.put(f.shipment)
        val updateJson = Json.obj("expectedVersion" -> f.shipment.version,
                                  "trackingNumber"     -> "")
        val json = makeRequest(POST, uri(f.shipment, "trackingnumber"), BAD_REQUEST, updateJson)

        (json \ "status").as[String] must include ("error")

        (json \ "message").as[String] must include ("TrackingNumberInvalid")
      }

      "must not allow updating the tracking number on a shipment not in created state"  in {
        val f = allShipmentsFixture

        nonCreatedStates.foreach { state =>
          val shipment = f.shipments(state)
          shipmentRepository.put(shipment)
          val updateJson = Json.obj("expectedVersion" -> shipment.version,
                                    "trackingNumber"  -> nameGenerator.next[String])

          val json = makeRequest(POST, uri(shipment, "trackingnumber"), BAD_REQUEST, updateJson)

          (json \ "status").as[String] must include ("error")

          (json \ "message").as[String] must include regex ("InvalidState: shipment not created")
        }
      }

    }

    "POST /shipments/fromlocation/:id" must {

      "allow updating the location the shipment is from" in {
        val f = createdShipmentFixture
        shipmentRepository.put(f.shipment)

        val newLocation = factory.createLocation
        val centre = factory.createEnabledCentre.copy(locations = Set(newLocation))
        centreRepository.put(centre)

        val updateJson = Json.obj("expectedVersion" -> f.shipment.version,
                                  "locationId"      -> newLocation.uniqueId.id)
        val json = makeRequest(POST, uri(f.shipment, "fromlocation"), updateJson)
                              (json \ "status").as[String] must include ("success")

        shipmentRepository.getByKey(f.shipment.id) mustSucceed { repoShipment =>
          repoShipment mustBe a[CreatedShipment]
          compareObj((json \ "data").as[JsObject], repoShipment)

          repoShipment must have (
            'id             (f.shipment.id),
            'version        (f.shipment.version + 1),
            'courierName    (f.shipment.courierName),
            'trackingNumber (f.shipment.trackingNumber),
            'fromLocationId (newLocation.uniqueId.id),
            'toLocationId   (f.shipment.toLocationId.id))

          checkTimeStamps(repoShipment, f.shipment.timeAdded, DateTime.now)
          compareTimestamps(repoShipment, f.shipment)
        }
      }

      "not allow updating the from location to an empty string" in {
        val f = createdShipmentFixture
        shipmentRepository.put(f.shipment)
        val updateJson = Json.obj("expectedVersion" -> f.shipment.version,
                                  "locationId"      -> "")
        val json = makeRequest(POST, uri(f.shipment, "fromlocation"), NOT_FOUND, updateJson)

        (json \ "status").as[String] must include ("error")

        (json \ "message").as[String] must include regex ("EntityCriteriaError.*centre with location id")
      }

      "not allow updating the from location to an invalid id" in {
        val f = createdShipmentFixture
        shipmentRepository.put(f.shipment)

        val badLocation = factory.createLocation

        val updateJson = Json.obj("expectedVersion" -> f.shipment.version,
                                  "locationId"      -> badLocation.uniqueId.id)
        val json = makeRequest(POST, uri(f.shipment, "fromlocation"), NOT_FOUND, updateJson)

        (json \ "status").as[String] must include ("error")

        (json \ "message").as[String] must include regex ("EntityCriteriaError.*centre with location id")
      }

      "must not allow updating the from location on a shipment not in created state"  in {
        val f = allShipmentsFixture
        val badLocation = factory.createLocation

        nonCreatedStates.foreach { state =>
          val shipment = f.shipments(state)
          shipmentRepository.put(shipment)
          val updateJson = Json.obj("expectedVersion" -> shipment.version,
                                    "locationId"      -> badLocation.uniqueId.id)

          val json = makeRequest(POST, uri(shipment, "fromlocation"), BAD_REQUEST, updateJson)

          (json \ "status").as[String] must include ("error")

          (json \ "message").as[String] must include regex ("InvalidState: shipment not created")
        }
      }

    }

    "POST /shipments/tolocation/:id" must {

      "allow updating the location the shipment is going to" in {
        val f = createdShipmentFixture
        shipmentRepository.put(f.shipment)

        val newLocation = factory.createLocation
        val centre = factory.createEnabledCentre.copy(locations = Set(newLocation))
        centreRepository.put(centre)

        val updateJson = Json.obj("expectedVersion" -> f.shipment.version,
                                  "locationId"      -> newLocation.uniqueId.id)
        val json = makeRequest(POST, uri(f.shipment, "tolocation"), updateJson)
                              (json \ "status").as[String] must include ("success")

        shipmentRepository.getByKey(f.shipment.id) mustSucceed { repoShipment =>
          repoShipment mustBe a[CreatedShipment]
          compareObj((json \ "data").as[JsObject], repoShipment)

          repoShipment must have (
            'id             (f.shipment.id),
            'version        (f.shipment.version + 1),
            'courierName    (f.shipment.courierName),
            'trackingNumber (f.shipment.trackingNumber),
            'fromLocationId (f.shipment.fromLocationId.id),
            'toLocationId   (newLocation.uniqueId.id))

          checkTimeStamps(repoShipment, f.shipment.timeAdded, DateTime.now)
          compareTimestamps(repoShipment, f.shipment)
        }
      }

      "not allow updating the TO location to an empty string" in {
        val f = createdShipmentFixture
        shipmentRepository.put(f.shipment)
        val updateJson = Json.obj("expectedVersion" -> f.shipment.version,
                                  "locationId"      -> "")
        val json = makeRequest(POST, uri(f.shipment, "tolocation"), NOT_FOUND, updateJson)

        (json \ "status").as[String] must include ("error")

        (json \ "message").as[String] must include regex ("EntityCriteriaError.*centre with location id")
      }

      "not allow updating the TO location to an invalid id" in {
        val f = createdShipmentFixture
        shipmentRepository.put(f.shipment)

        val badLocation = factory.createLocation

        val updateJson = Json.obj("expectedVersion" -> f.shipment.version,
                                  "locationId"      -> badLocation.uniqueId.id)
        val json = makeRequest(POST, uri(f.shipment, "tolocation"), NOT_FOUND, updateJson)

        (json \ "status").as[String] must include ("error")

        (json \ "message").as[String] must include regex ("EntityCriteriaError.*centre with location id")
      }

      "must not allow updating the TO location on a shipment not in created state"  in {
        val f = allShipmentsFixture
        val badLocation = factory.createLocation

        nonCreatedStates.foreach { state =>
          val shipment = f.shipments(state)
          shipmentRepository.put(shipment)
          val updateJson = Json.obj("expectedVersion" -> shipment.version,
                                    "locationId"      -> badLocation.uniqueId.id)

          val json = makeRequest(POST, uri(shipment, "tolocation"), BAD_REQUEST, updateJson)

          (json \ "status").as[String] must include ("error")

          (json \ "message").as[String] must include regex ("InvalidState: shipment not created")
        }
      }

    }

    "POST /shipments/state/:id" must {

      def changeStateCommon(shipment:  Shipment,
                            newState:  EntityState,
                            timeMaybe: Option[DateTime]) = {
        shipmentRepository.put(shipment)
        val baseJson = Json.obj("expectedVersion" -> shipment.version)
        val updateJson = timeMaybe.fold { baseJson } { time =>
            baseJson ++ Json.obj("datetime" -> time) }

        val json = makeRequest(POST, uri(shipment, s"state/$newState"), updateJson)

        (json \ "status").as[String] must include ("success")

        shipmentRepository.getByKey(shipment.id) mustSucceed { repoShipment =>
          compareObj((json \ "data").as[JsObject], repoShipment)

          repoShipment must have (
            'id             (shipment.id),
            'version        (shipment.version + 1),
            'courierName    (shipment.courierName),
            'trackingNumber (shipment.trackingNumber),
            'fromLocationId (shipment.fromLocationId.id),
            'toLocationId   (shipment.toLocationId.id))

          checkTimeStamps(repoShipment, shipment.timeAdded, DateTime.now)
        }
      }

      "for all states" should {

        "fail requests to update the state on a shipment that does not exist" in {
          val f = createdShipmentFixture
          val time = DateTime.now.minusDays(10)

          forAll(states) { state =>
            info(s"for $state state")
            val updateJson = Json.obj("expectedVersion" -> f.shipment.version,
                                      "datetime"        -> time)
            val json = makeRequest(POST, uri(f.shipment, s"state/$state"), NOT_FOUND, updateJson)

            (json \ "status").as[String] must include ("error")

            (json \ "message").as[String] must include regex ("IdNotFound.*shipment.*")
          }
        }

      }

      "for CREATED state" should {

        "change to CREATED state from PACKED state" in {
          val f = packedShipmentFixture

          changeStateCommon(f.shipment, Shipment.createdState, None)
          shipmentRepository.getByKey(f.shipment.id) mustSucceed { repoShipment =>
            repoShipment mustBe a[CreatedShipment]
            compareTimestamps(shipment     = repoShipment,
                              timePacked   = None,
                              timeSent     = None,
                              timeReceived = None,
                              timeUnpacked = None)
          }
        }

        "not change to CREATED state from a state other than PACKED" in {
          val f = allShipmentsFixture
          f.shipments.values.foreach(shipmentRepository.put)

          val states = Table("state",
                             Shipment.sentState,
                             Shipment.receivedState,
                             Shipment.unpackedState,
                             Shipment.lostState)

          forAll(states) { state =>
            info(s"from $state state")
            val shipment = f.shipments(state)
            val updateJson = Json.obj("expectedVersion" -> shipment.version)
            val json = makeRequest(POST, uri(shipment, s"state/created"), BAD_REQUEST, updateJson)

            (json \ "status").as[String] must include ("error")

            (json \ "message").as[String] must include ("InvalidState: shipment is not packed")
          }
        }
      }

      "for PACKED state" must {

        "change to PACKED state from other valid states" in {
          val f = allShipmentsFixture
          val testStates = Table("state", Shipment.createdState, Shipment.sentState)

          forAll(testStates) { state =>
            info(s"change to $state state")

            val stateChangeTime = DateTime.now.minusDays(10)

            val shipment = f.shipments(state)
            changeStateCommon(shipment, Shipment.packedState, Some(stateChangeTime))
            shipmentRepository.getByKey(shipment.id) mustSucceed { repoShipment =>
              repoShipment mustBe a[PackedShipment]
              compareTimestamps(repoShipment,
                                Some(stateChangeTime),
                                None,
                                None,
                                None)
            }
          }
        }

        "not change to PACKED state from an invalid state" in {
          val f = allShipmentsFixture
          f.shipments.values.foreach(shipmentRepository.put)

          val states = Table("state",
                             Shipment.packedState,
                             Shipment.receivedState,
                             Shipment.unpackedState,
                             Shipment.lostState)

          forAll(states) { state =>
            info(s"from state $state")
            val shipment = f.shipments(state)
            val updateJson = Json.obj("expectedVersion" -> shipment.version,
                                      "datetime"        -> DateTime.now)
            val json = makeRequest(POST, uri(shipment, s"state/packed"), BAD_REQUEST, updateJson)

            (json \ "status").as[String] must include ("error")

            (json \ "message").as[String] must include ("InvalidState: cannot change to packed state")
          }
        }
      }

      "for SENT state" must {

        "change to SENT state from" in {
          val f = allShipmentsFixture

          val testStates = Table("state name",
                                 Shipment.packedState,
                                 Shipment.receivedState)

          forAll(testStates) { state =>
            info(s"$state state")
            val shipment =  f.shipments(state)
            val time = shipment.timePacked.get.plusDays(1)

            changeStateCommon(shipment, Shipment.sentState, Some(time))
            shipmentRepository.getByKey(shipment.id) mustSucceed { repoShipment =>
              repoShipment mustBe a[SentShipment]
              compareTimestamps(repoShipment,
                                shipment.timePacked,
                                Some(time),
                                None,
                                None)
            }
          }
        }

        "fail when updating state to SENT where time is less than packed time" in {
          val f = packedShipmentFixture
          shipmentRepository.put(f.shipment)
          val updateJson = Json.obj("expectedVersion" -> f.shipment.version,
                                    "datetime"        -> f.shipment.timePacked.get.minusDays(1))

          val json = makeRequest(POST, uri(f.shipment, "state/sent"), BAD_REQUEST, updateJson)

          (json \ "status").as[String] must include ("error")

          (json \ "message").as[String] must include ("TimeSentBeforePacked")
        }

        "not change to SENT state from an invalid state" in {
          val f = allShipmentsFixture
          f.shipments.values.foreach(shipmentRepository.put)

          val states = Table("state",
                             Shipment.createdState,
                             Shipment.sentState,
                             Shipment.unpackedState,
                             Shipment.lostState)

          forAll(states) { state =>
            info(s"from state $state")
            val shipment = f.shipments(state)
            val updateJson = Json.obj("expectedVersion" -> shipment.version,
                                      "datetime"        -> DateTime.now)
            val json = makeRequest(POST, uri(shipment, s"state/sent"), BAD_REQUEST, updateJson)

            (json \ "status").as[String] must include ("error")

            (json \ "message").as[String] must include ("InvalidState: cannot change to sent state")
          }
        }

      }

      "for RECEIVED state" must {

        "change to RECEIVED state from" in {
          val f = allShipmentsFixture

          val testStates = Table("state name",
                                 Shipment.sentState,
                                 Shipment.unpackedState)

          forAll(testStates) { state =>
            info(s"$state state")
            val shipment =  f.shipments(state)
            val time = shipment.timeSent.get.plusDays(1)

            changeStateCommon(shipment, Shipment.receivedState, Some(time))
            shipmentRepository.getByKey(shipment.id) mustSucceed { repoShipment =>
              repoShipment mustBe a[ReceivedShipment]
              compareTimestamps(repoShipment,
                                shipment.timePacked,
                                shipment.timeSent,
                                Some(time),
                                None)
            }
          }
        }

        "not change to RECEIVED state from an invalid state" in {
          val f = allShipmentsFixture
          f.shipments.values.foreach(shipmentRepository.put)

          val states = Table("state",
                             Shipment.createdState,
                             Shipment.packedState,
                             Shipment.receivedState,
                             Shipment.lostState)

          forAll(states) { state =>
            info(s"from state $state")
            val shipment = f.shipments(state)
            val updateJson = Json.obj("expectedVersion" -> shipment.version,
                                      "datetime"        -> DateTime.now)
            val json = makeRequest(POST, uri(shipment, s"state/received"), BAD_REQUEST, updateJson)

            (json \ "status").as[String] must include ("error")

            (json \ "message").as[String] must include ("InvalidState: cannot change to received state")
          }
        }

        "fail for updating state to RECEIVED where time is less than sent time" in {
          val f = sentShipmentFixture
          shipmentRepository.put(f.shipment)
          val updateJson = Json.obj("expectedVersion" -> f.shipment.version,
                                    "datetime"        -> f.shipment.timeSent.get.minusDays(1))

          val json = makeRequest(POST, uri(f.shipment, "state/received"), BAD_REQUEST, updateJson)

          (json \ "status").as[String] must include ("error")

          (json \ "message").as[String] must include ("TimeReceivedBeforeSent")
        }

        "fail to change from UNPACKED to RECEIVED if some specimens are not in PRESENT state" in {
          val f = specimensFixture(1)

          val shipment = makeUnpackedShipment(f.shipment)
          shipmentRepository.put(shipment)

          val specimen = f.specimens.head
          val shipmentSpecimen = factory.createShipmentSpecimen.copy(shipmentId = f.shipment.id,
                                                                     specimenId = specimen.id,
                                                                     state      = ShipmentItemState.Received)
          shipmentSpecimenRepository.put(shipmentSpecimen)

          val updateJson = Json.obj("expectedVersion" -> shipment.version,
                                    "datetime"        -> shipment.timeReceived)

          val json = makeRequest(POST, uri(f.shipment, "state/received"), BAD_REQUEST, updateJson)

          (json \ "status").as[String] must include ("error")

          (json \ "message").as[String] must include (
            "InvalidState: cannot change to received state, items have already been processed")
        }

        "fail to change from UNPACKED to RECEIVED if some containers are not in PRESENT state" ignore {
          fail("needs implementation")
        }
      }

      "for UNPACKED state" must {

        "change to UNPACKED state from RECEIVED" in {
          val f = receivedShipmentFixture
          val timeUnpacked = f.shipment.timeReceived.get.plusDays(1)

          changeStateCommon(f.shipment, Shipment.unpackedState, Some(timeUnpacked))
          shipmentRepository.getByKey(f.shipment.id) mustSucceed { repoShipment =>
            repoShipment mustBe a[UnpackedShipment]
            compareTimestamps(repoShipment,
                              f.shipment.timePacked,
                              f.shipment.timeSent,
                              f.shipment.timeReceived,
                              Some(timeUnpacked))
          }
        }

        "not change to UNPACKED state from an invalid state" in {
          val f = allShipmentsFixture
          f.shipments.values.foreach(shipmentRepository.put)

          val states = Table("state",
                             Shipment.createdState,
                             Shipment.packedState,
                             Shipment.sentState,
                             Shipment.lostState)

          forAll(states) { state =>
            info(s"from state $state")
            val shipment = f.shipments(state)
            val updateJson = Json.obj("expectedVersion" -> shipment.version,
                                      "datetime"        -> DateTime.now)
            val json = makeRequest(POST, uri(shipment, s"state/unpacked"), BAD_REQUEST, updateJson)

            (json \ "status").as[String] must include ("error")

            (json \ "message").as[String] must include ("InvalidState: cannot change to unpacked state")
          }
        }

      }

      "for LOST state" must {

        "allow setting a shipment's state to LOST" in {
          val f = sentShipmentFixture

          changeStateCommon(f.shipment, Shipment.lostState, None)
          shipmentRepository.getByKey(f.shipment.id) mustSucceed { repoShipment =>
            repoShipment mustBe a[LostShipment]
            compareTimestamps(f.shipment, repoShipment)
          }
        }

        "not change to LOST state from an invalid state" in {
          val f = allShipmentsFixture
          f.shipments.values.foreach(shipmentRepository.put)

          val states = Table("state",
                             Shipment.createdState,
                             Shipment.packedState,
                             Shipment.receivedState,
                             Shipment.unpackedState)

          forAll(states) { state =>
            info(s"from state $state")
            val shipment = f.shipments(state)
            val updateJson = Json.obj("expectedVersion" -> shipment.version,
                                      "datetime"        -> DateTime.now)
            val json = makeRequest(POST, uri(shipment, s"state/lost"), BAD_REQUEST, updateJson)

            (json \ "status").as[String] must include ("error")

            (json \ "message").as[String] must include ("InvalidState: cannot change to lost state")
          }
        }
      }

    }

    "POST /shipments/state/skip-to-sent/:id" must {

      "switch from CREATED to SENT state" in {
        val f = createdShipmentFixture
        val timePacked = DateTime.now.minusDays(10)
        val timeSent = timePacked.plusDays(1)
        val updateJson = Json.obj("expectedVersion" -> f.shipment.version,
                                  "timePacked"      -> timePacked,
                                  "timeSent"        -> timeSent)

        skipStateCommon(f.shipment, "state/skip-to-sent", updateJson)
        shipmentRepository.getByKey(f.shipment.id) mustSucceed { repoShipment =>
          repoShipment mustBe a[SentShipment]
          compareTimestamps(shipment     = repoShipment,
                            timePacked   = Some(timePacked),
                            timeSent     = Some(timeSent),
                            timeReceived = None,
                            timeUnpacked = None)
        }
      }

      "fails when skipping to SENT state from state" in {
        val f = allShipmentsFixture

        val fromStates = Table("from states",
                               Shipment.packedState,
                               Shipment.sentState,
                               Shipment.receivedState,
                               Shipment.unpackedState,
                               Shipment.lostState)
        forAll(fromStates) { fromState =>
          info(s"$fromState")
          val shipment =  f.shipments(fromState)
          val time = DateTime.now.minusDays(10)
          val updateJson = Json.obj("expectedVersion" -> shipment.version,
                                    "timePacked"      -> time,
                                    "timeSent"        -> time)
          shipmentRepository.put(shipment)
          val json = makeRequest(POST, uri(shipment, "state/skip-to-sent"), BAD_REQUEST, updateJson)

          (json \ "status").as[String] must include ("error")

          (json \ "message").as[String] must include regex ("InvalidState: shipment not created")
        }
      }

    }

    "POST /shipments/state/skip-to-unpacked/:id" must {

      "switch from SENT to UNPACKED state" in {
        val f = sentShipmentFixture
        val timeReceived = f.shipment.timeSent.fold { DateTime.now } { t => t.plusDays(1) }
        val timeUnpacked = timeReceived.plusDays(1)
        val updateJson = Json.obj("expectedVersion" -> f.shipment.version,
                                  "timeReceived"    -> timeReceived,
                                  "timeUnpacked"    -> timeUnpacked)
        skipStateCommon(f.shipment, "state/skip-to-unpacked", updateJson)
        shipmentRepository.getByKey(f.shipment.id) mustSucceed { repoShipment =>
          compareTimestamps(shipment     = repoShipment,
                            timePacked   = f.shipment.timePacked,
                            timeSent     = f.shipment.timeSent,
                            timeReceived = Some(timeReceived),
                            timeUnpacked = Some(timeUnpacked))
        }
      }

      "fails when skipping to UNPACKED state from state" in {
        val f = allShipmentsFixture

        val fromStates = Table("from states",
                               Shipment.createdState,
                               Shipment.packedState,
                               Shipment.receivedState,
                               Shipment.unpackedState,
                               Shipment.lostState)
        forAll(fromStates) { fromState =>
          info(s"$fromState")
          val shipment =  f.shipments(fromState)
          val time = shipment.timeSent.fold { DateTime.now } { t => t }
          val updateJson = Json.obj("expectedVersion" -> shipment.version,
                                    "timeReceived"    -> time,
                                    "timeUnpacked"    -> time)
          shipmentRepository.put(shipment)
          val json = makeRequest(POST, uri(shipment, "state/skip-to-unpacked"), BAD_REQUEST, updateJson)

          (json \ "status").as[String] must include ("error")

          (json \ "message").as[String] must include regex ("InvalidState: shipment not sent")
        }
      }

    }

    "DELETE /shipments/:id/:ver" must {

      "must delete a shipment in created state" in {
        val f = createdShipmentFixture
        shipmentRepository.put(f.shipment)

        val json = makeRequest(DELETE, uri(f.shipment) + s"/${f.shipment.version}")

        (json \ "status").as[String] must include ("success")

        shipmentRepository.getByKey(f.shipment.id) mustFail "IdNotFound.*shipment id.*"
      }

      "fail on attempt to delete a shipment not in the system"  in {
        val f = createdShipmentFixture

        val json = makeRequest(DELETE, uri(f.shipment) + s"/${f.shipment.version}", NOT_FOUND)

        (json \ "status").as[String] must include ("error")

        (json \ "message").as[String] must include regex ("IdNotFound.*shipment id")
      }

      "must not delete a shipment not in created state"  in {
        val f = allShipmentsFixture

        nonCreatedStates.foreach { state =>
          val shipment = f.shipments(state)
          shipmentRepository.put(shipment)

          val json = makeRequest(DELETE, uri(shipment) + s"/${shipment.version}", BAD_REQUEST)

          (json \ "status").as[String] must include ("error")

          (json \ "message").as[String] must include regex ("InvalidState: shipment not created")
        }
      }

      "attempt to remove a shipment containing specimens fails" in {
        val f = specimensFixture(1)

        val specimen = f.specimens.head
        val shipmentSpecimen = factory.createShipmentSpecimen.copy(shipmentId = f.shipment.id,
                                                                   specimenId = specimen.id)
        shipmentSpecimenRepository.put(shipmentSpecimen)

        val json = makeRequest(DELETE, uri(f.shipment) + s"/${f.shipment.version}", BAD_REQUEST)

        (json \ "status").as[String] must include ("error")

        (json \ "message").as[String] must include regex ("shipment has specimens.*")
      }
    }
  }

}
