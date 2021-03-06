/**
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2016 Canadian BioSample Repository (CBSR)
 */
define(['lodash'], function (_) {
  'use strict';

  var component = {
    templateUrl : '/assets/javascripts/centres/components/shipmentAdd/shipmentAdd.html',
    controller: ShipmentAddController,
    controllerAs: 'vm',
    bindings: {}
  };

  ShipmentAddController.$inject = [
    '$state',
    'gettextCatalog',
    'Centre',
    'Shipment',
    'domainNotificationService',
    'notificationsService',
    'shipmentSendProgressItems'
  ];

  /**
   *
   */
  function ShipmentAddController($state,
                                 gettextCatalog,
                                 Centre,
                                 Shipment,
                                 domainNotificationService,
                                 notificationsService,
                                 shipmentSendProgressItems) {
    var vm = this;

    vm.progressInfo = {
      items: shipmentSendProgressItems,
      current: 1
    };

    vm.hasValidCentres           = false;
    vm.shipment                  = new Shipment();
    vm.$onInit                   = onInit;
    vm.submit                    = submit;
    vm.cancel                    = cancel;
    vm.getFromCentreLocationInfo = getFromCentreLocationInfo;
    vm.getToCentreLocationInfo   = getToCentreLocationInfo;

    //--

    function onInit() {
      return Centre.locationsSearch('').then(function (results) {
        vm.hasValidCentres = (results.length > 1);
      });
    }

    function submit(specimenSpec) {
      vm.shipment.add().then(onAddSuccessful).catch(onAddFailed);

      function onAddSuccessful(shipment) {
        $state.go('home.shipping.addItems', { shipmentId: shipment.id });
      }

      function onAddFailed(error) {
        domainNotificationService.updateErrorModal(error, gettextCatalog.getString('shipment'));
      }
    }

    function cancel() {
      $state.go('home.shipping');
    }

    function getCentreLocationInfo(filter, locationIdsToOmit) {
      return Centre.locationsSearch(filter)
        .then(function (locations) {
          _.remove(locations, function (location) {
            return _.includes(locationIdsToOmit, location.locationId);
          });
          return locations;
        });
    }

    function getFromCentreLocationInfo(filter) {
      var locationIdsToOmit = [];
      if (vm.shipment.toLocationInfo) {
        locationIdsToOmit.push(vm.shipment.toLocationInfo.locationId);
      }
      return getCentreLocationInfo(filter, locationIdsToOmit);
    }

    function getToCentreLocationInfo(filter) {
      var locationIdsToOmit = [];
      if (vm.shipment.fromLocationInfo) {
        locationIdsToOmit.push(vm.shipment.fromLocationInfo.locationId);
      }
      return getCentreLocationInfo(filter, locationIdsToOmit);
    }
  }

  return component;
});
