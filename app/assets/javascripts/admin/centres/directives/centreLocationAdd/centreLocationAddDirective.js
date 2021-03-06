/**
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2016 Canadian BioSample Repository (CBSR)
 */
define(function () {
  'use strict';

  /**
   *
   */
  function centreLocationAddDirective() {
    var directive = {
      restrict: 'E',
      scope: {},
      bindToController: {
        centre: '='
      },
      templateUrl : '/assets/javascripts/admin/centres/directives/centreLocationAdd/centreLocationAdd.html',
      controller: CentreLocationAddCtrl,
      controllerAs: 'vm'
    };

    return directive;
  }

  CentreLocationAddCtrl.$inject = [
    '$state',
    'gettextCatalog',
    'domainNotificationService',
    'notificationsService'
  ];

  function CentreLocationAddCtrl($state,
                                 gettextCatalog,
                                 domainNotificationService,
                                 notificationsService) {
    var vm = this;

    vm.submit = submit;
    vm.cancel = cancel;
    vm.returnStateName = 'home.admin.centres.centre.locations';

    //---

    function submit(location) {
      vm.centre.addLocation(location)
        .then(submitSuccess)
        .catch(submitError);

      //--

      function submitSuccess() {
        notificationsService.submitSuccess();
        $state.go(vm.returnStateName, {}, { reload: true });
      }

      function submitError(error) {
        return domainNotificationService.updateErrorModal(error, gettextCatalog.getString('location'));
      }
    }

    function cancel() {
      $state.go(vm.returnStateName);
    }

  }

  return centreLocationAddDirective;
});
