/**
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2016 Canadian BioSample Repository (CBSR)
 */
define(function (require) {
  'use strict';

  var _ = require('lodash');

  centreLocationsModalService.$inject = [
    '$uibModal',
    'Centre'
  ];

  /*
   * Opens a modal that allows the user to select a centre location.
   */
  function centreLocationsModalService($uibModal,
                                       Centre) {
    var service = {
      open: openModal
    };
    return service;

    //-------

    function openModal(heading, label, placeholder, value, locationInfosToOmit) {
      var modal,
          locationIdsToOmit =  _.map(locationInfosToOmit, function (locInfo) {
            return locInfo.locationId;
          });

      ModalController.$inject = [];

      modal = $uibModal.open({
        templateUrl: '/assets/javascripts/centres/services/centreLocationsModal/centreLocationsModal.html',
        controller: ModalController,
        controllerAs: 'vm',
        backdrop: true,
        keyboard: true,
        modalFade: true
      });

      return modal;

      //--

      function ModalController() {
        var vm = this;

        vm.heading               = heading;
        vm.label                 = label;
        vm.placeholder           = placeholder;
        vm.locationInfo          = value;
        vm.centreLocations       = undefined;

        vm.okPressed             = okPressed;
        vm.closePressed          = closePressed;
        vm.getCentreLocationInfo = getCentreLocationInfo;

        //--

        function okPressed() {
          modal.close(vm.locationInfo);
        }

        function closePressed() {
          modal.dismiss('cancel');
        }

        function getCentreLocationInfo(filter) {
          return Centre.locationsSearch(filter)
            .then(function (locations) {
              _.remove(locations, function (location) {
                return _.includes(locationIdsToOmit, location.locationId);
              });
              return locations;
            });
        }
      }
    }

  }

  return centreLocationsModalService;
});
