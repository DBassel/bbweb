define([], function() {
  'use strict';

  SpecimenGroupEditCtrl.$inject = [
    '$state',
    'domainEntityService',
    'specimenGroupsService',
    'notificationsService',
    'valueTypes',
    'study',
    'specimenGroup'
  ];

  /**
   * Add or update an specimen Group.
   */
  function SpecimenGroupEditCtrl($state,
                                 domainEntityService,
                                 specimenGroupsService,
                                 notificationsService,
                                 valueTypes,
                                 study,
                                 specimenGroup) {
    var action = specimenGroup.id ? 'Update' : 'Add';

    var vm = this;
    vm.title =  action + ' Specimen Group';
    vm.study = study;
    vm.specimenGroup = specimenGroup;

    vm.anatomicalSourceTypes = valueTypes.anatomicalSourceType.sort();
    vm.preservTypes          = valueTypes.preservationType.sort();
    vm.preservTempTypes      = valueTypes.preservationTemperatureType.sort();
    vm.specimenTypes         = valueTypes.specimenType.sort();

    vm.submit = submit;
    vm.cancel = cancel;

    //--

    function gotoReturnState() {
      return $state.go('home.admin.studies.study.specimens', {}, {reload: true});
    }

    function submitSuccess() {
      notificationsService.submitSuccess();
      gotoReturnState();
    }

    function submit(specimenGroup) {
      specimenGroupsService.addOrUpdate(specimenGroup)
        .then(submitSuccess)
        .catch(function(error) {
          domainEntityService.updateErrorModal(
            error,'specimen link type');
        });
    }

    function cancel() {
      gotoReturnState();
    }

  }

  return SpecimenGroupEditCtrl;
});
