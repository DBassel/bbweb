define(['underscore'], function(_) {
  'use strict';

  CollectionDtoFactory.$inject = [
    'funutils',
    'validationService',
    'biobankApi',
    'CollectionEventType',
    'CollectionEventAnnotationType',
    'SpecimenGroup'
  ];

  function CollectionDtoFactory(funutils,
                                validationService,
                                biobankApi,
                                CollectionEventType,
                                CollectionEventAnnotationType,
                                SpecimenGroup) {

    var requiredKeys = [
      'collectionEventTypes',
      'collectionEventAnnotationTypes',
      'collectionEventAnnotationTypeIdsInUse',
      'specimenGroups'
    ];

    var validateObj = funutils.partial(
      validationService.condition1(
        validationService.validator('must be a map', _.isObject),
        validationService.validator('has the correct keys',
                                    validationService.hasKeys.apply(null, requiredKeys))),
      _.identity);

    /**
     * An object that contains a list of all the collectionEventTypes, collectionEventAnnotationTypes, and
     * specimenGroups for a study.
     */
    function CollectionDto(obj) {
      var self = this;

      obj = obj || {};

      obj.collectionEventAnnotationTypes = _.map(
        obj.collectionEventAnnotationTypes,
        function (serverAt) {
          return new CollectionEventAnnotationType(serverAt);
        });
      obj.specimenGroups = _.map(obj.specimenGroups, function(serverSg) {
        return new SpecimenGroup(serverSg);
      });
      obj.collectionEventTypes = _.map(obj.collectionEventTypes, function(serverCet) {
        var cet = new CollectionEventType(serverCet);
        cet.studySpecimenGroups(obj.specimenGroups);
        cet.studyAnnotationTypes(obj.collectionEventAnnotationTypes);
        return cet;
      });

      _.extend(self, _.defaults(obj, {
        collectionEventTypes:                  [],
        collectionEventAnnotationTypes:        [],
        collectionEventAnnotationTypeIdsInUse: [],
        specimenGroups:                        []
      }));
    }

    /**
     * Used by promise code, so it must return an error rather than throw one.
     */
    CollectionDto.create = function (obj) {
      var validation = validateObj(obj);
      if (!_.isObject(validation)) {
        return new Error('invalid object from server: ' + validation);
      }
      return new CollectionDto(obj);
    };

    CollectionDto.get = function(studyId) {
      return biobankApi.get('/studies/' + studyId + '/dto/collection')
        .then(function(reply) {
          return CollectionDto.create(reply);
        });
    };

    return CollectionDto;
  }

  return CollectionDtoFactory;

});
