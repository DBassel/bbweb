/**
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2016 Canadian BioSample Repository (CBSR)
 */
define(['lodash'], function(_) {
  'use strict';

  /**
   *
   */
  function locationAddDirective() {
    var directive = {
      restrict: 'E',
      scope: {},
      bindToController: {
        // a function that takes a location as a parameter and returns a promise (after accepting the new
        // location)
        onSubmit: '&',
        onCancel: '&'
      },
      templateUrl : '/assets/javascripts/admin/directives/locationAdd/locationAdd.html',
      controller: LocationAddCtrl,
      controllerAs: 'vm'
    };

    return directive;
  }

  LocationAddCtrl.$inject = [
    '$state',
    'Location',
    'domainNotificationService',
    'notificationsService'
  ];

  function LocationAddCtrl($state,
                           Location,
                           domainNotificationService,
                           notificationsService) {
    var vm = this;

    vm.location = new Location();
    vm.submit = submit;
    vm.cancel = cancel;

    //--

    function submit(location) {
      vm.onSubmit()(location);
    }

    function cancel() {
      vm.onCancel()();
    }
  }

  return locationAddDirective;
});
