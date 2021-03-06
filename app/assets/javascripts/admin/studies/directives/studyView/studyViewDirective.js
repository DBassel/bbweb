/**
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2016 Canadian BioSample Repository (CBSR)
 */
define(['lodash'], function (_) {
  'use strict';

  /**
   *
   */
  function studyViewDirective() {
    var directive = {
      restrict: 'E',
      scope: {},
      bindToController: {
        study: '='
      },
      templateUrl : '/assets/javascripts/admin/studies/directives/studyView/studyView.html',
      controller: StudyViewCtrl,
      controllerAs: 'vm'
    };

    return directive;
  }

  StudyViewCtrl.$inject = [
    '$controller',
    '$scope',
    '$window',
    '$state',
    'gettextCatalog'
  ];

  function StudyViewCtrl($controller,$scope, $window, $state, gettextCatalog) {
    var vm = this;

    // initialize this controller's base class
    $controller('TabbedPageController',
                {
                  vm:     vm,
                  $scope: $scope,
                  $state: $state
                });

    vm.tabs = [
      {
        heading: gettextCatalog.getString('Summary'),
        sref: 'home.admin.studies.study.summary',
        active: true
      },
      {
        heading: gettextCatalog.getString('Participants'),
        sref: 'home.admin.studies.study.participants',
        active: false
      },
      {
        heading: gettextCatalog.getString('Collection'),
        sref: 'home.admin.studies.study.collection',
        active: false
      },
      {
        heading: gettextCatalog.getString('Processing'),
        sref: 'home.admin.studies.study.processing',
        active: false
      }
    ];

    init();

    //--

    /**
     * Initialize the panels to open state when viewing a new study.
     */
    function init() {
      if (vm.study.id !== $window.localStorage.getItem('study.panel.studyId')) {
        // this way when the user selects a new study, the panels always default to open
        $window.localStorage.setItem('study.panel.processingTypes',                true);
        $window.localStorage.setItem('study.panel.specimenLinkAnnotationTypes',    true);
        $window.localStorage.setItem('study.panel.specimenLinkTypes',              true);

        // remember the last viewed study
        $window.localStorage.setItem('study.panel.studyId', vm.study.id);
      }
    }
  }

  return studyViewDirective;
});
