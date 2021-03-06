/**
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2015 Canadian BioSample Repository (CBSR)
 */
define(['lodash'], function (_) {
  'use strict';

  /**
   *
   */
  function participantViewDirective() {
    var directive = {
      restrict: 'E',
      scope: {},
      bindToController: {
        study: '=',
        participant: '='
      },
      templateUrl : '/assets/javascripts/collection/directives/participantView/participantView.html',
      controller: ParticipantViewCtrl,
      controllerAs: 'vm'
    };
    return directive;
  }

  ParticipantViewCtrl.$inject = [
    '$window',
    '$scope',
    '$state',
    'gettextCatalog',
    'stateHelper'
  ];

  /**
   *
   */
  function ParticipantViewCtrl($window,
                               $scope,
                               $state,
                               gettextCatalog,
                               stateHelper) {
    var vm = this;

    vm.tabs = [
      {
        heading: gettextCatalog.getString('Summary'),
        sref: 'home.collection.study.participant.summary',
        active: false
      },
      {
        heading: gettextCatalog.getString('Collection'),
        sref: 'home.collection.study.participant.cevents',
        active: false
      }
    ];

    init();

    //--

    function init() {
      _.each(vm.tabs, function (tab, index) {
        tab.active = ($state.current.name.indexOf(tab.sref) >= 0);
        if (tab.active) {
          vm.active = index;
        }
      });

      $scope.$on('gettextLanguageChanged', function () {
        stateHelper.reloadAndReinit();
      });
    }

  }

  return participantViewDirective;
});
