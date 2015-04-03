/**
 * Configure routes of specimen groups module.
 */
define([], function() {
  'use strict';

  config.$inject = [
    '$urlRouterProvider',
    '$stateProvider',
    'authorizationProvider'
  ];

  function config($urlRouterProvider,
                  $stateProvider,
                  authorizationProvider
                 ) {

    resolveSpecimenGroups.$inject = ['$stateParams', 'SpecimenGroup'];
    function resolveSpecimenGroups($stateParams, SpecimenGroup) {
      return SpecimenGroup.list($stateParams.studyId);
    }

    resolveAnnotTypes.$inject = ['CollectionEventAnnotationType', 'study'];
    function resolveAnnotTypes(CollectionEventAnnotationType, study) {
      return CollectionEventAnnotationType.list(study.id);
    }

    $urlRouterProvider.otherwise('/');

    /**
     * Collection Event Type Add
     */
    $stateProvider.state('home.admin.studies.study.collection.ceventTypeAdd', {
      url: '/cetypes/add',
      resolve: {
        user: authorizationProvider.requireAuthenticatedUser,
        studySpecimenGroups: resolveSpecimenGroups,
        studyAnnotationTypes: resolveAnnotTypes,
        ceventType: [
          'CollectionEventType',
          'studySpecimenGroups',
          'studyAnnotationTypes',
          function(CollectionEventType,
                   studySpecimenGroups,
                   studyAnnotationTypes) {
            var cet = new CollectionEventType();
            cet.studySpecimenGroups(studySpecimenGroups);
            cet.studyAnnotationType(studyAnnotationTypes);
            return cet;
          }]
      },
      views: {
        'main@': {
          templateUrl: '/assets/javascripts/admin/studies/ceventTypes/ceventTypeForm.html',
          controller: 'CeventTypeEditCtrl as vm'
        }
      },
      data: {
        displayName: 'Collection Event Type'
      }
    });

    /**
     * Collection Event Type Update
     */
    $stateProvider.state('home.admin.studies.study.collection.ceventTypeUpdate', {
      url: '/cetypes/update/{ceventTypeId}',
      resolve: {
        user: authorizationProvider.requireAuthenticatedUser,
        studySpecimenGroups: resolveSpecimenGroups,
        studyAnnotationTypes: resolveAnnotTypes,
        ceventType: [
          '$stateParams',
          'CollectionEventType',
          'studySpecimenGroups',
          'studyAnnotationTypes',
          function($stateParams,
                   CollectionEventType,
                   studySpecimenGroups,
                   studyAnnotationTypes) {
            var cet = CollectionEventType.get($stateParams.studyId,
                                              $stateParams.ceventTypeId);
            cet.studySpecimenGroups(studySpecimenGroups);
            cet.studyAnnotationType(studyAnnotationTypes);
            return cet;
          }
        ]
      },
      views: {
        'main@': {
          templateUrl: '/assets/javascripts/admin/studies/ceventTypes/ceventTypeForm.html',
          controller: 'CeventTypeEditCtrl as vm'
        }
      },
      data: {
        displayName: 'Collection Event Type'
      }
    });

    /**
     * Collection Event Annotation Type Add
     */
    $stateProvider.state('home.admin.studies.study.collection.ceventAnnotTypeAdd', {
      url: '/cevent/annottype/add',
      resolve: {
        user: authorizationProvider.requireAuthenticatedUser,
        annotationType: ['CollectionEventAnnotationType', function(CollectionEventAnnotationType) {
          return new CollectionEventAnnotationType();
        }]
      },
      views: {
        'main@': {
          templateUrl: '/assets/javascripts/admin/studies/annotationTypes/annotationTypeForm.html',
          controller: 'AnnotationTypeEditCtrl as vm'
        }
      },
      data: {
        displayName: 'Collection Event Annotation Type'
      }
    });

    /**
     * Collection Event Annotation Type Update
     */
    $stateProvider.state('home.admin.studies.study.collection.ceventAnnotTypeUpdate', {
      url: '/cevent/annottype/update/{annotationTypeId}',
      resolve: {
        user: authorizationProvider.requireAuthenticatedUser,
        annotationType: [
          '$stateParams', 'CollectionEventAnnotationType',
          function($stateParams, CollectionEventAnnotationType) {
            return CollectionEventAnnotationType.get($stateParams.studyId,
                                                     $stateParams.annotationTypeId);
          }
        ]
      },
      views: {
        'main@': {
          templateUrl: '/assets/javascripts/admin/studies/annotationTypes/annotationTypeForm.html',
          controller: 'AnnotationTypeEditCtrl as vm'
        }
      },
      data: {
        displayName: 'Collection Event Annotation Type'
      }
    });

  }

  return config;
});
