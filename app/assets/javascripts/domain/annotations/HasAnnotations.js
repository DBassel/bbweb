/**
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2016 Canadian BioSample Repository (CBSR)
 */
define(['lodash'], function (_) {
  'use strict';

  HasAnnotationsFactory.$inject = [
    '$q',
    'biobankApi',
    'ConcurrencySafeEntity',
    'DomainError',
    'Annotation',
    'annotationFactory'
  ];

  /**
   * Mixin for entities that have annotations.
   *
   * Maintains an array of annotations.
   */
  function HasAnnotationsFactory($q,
                                 biobankApi,
                                 ConcurrencySafeEntity,
                                 DomainError,
                                 Annotation,
                                 annotationFactory) {

    function HasAnnotations() {}

    HasAnnotations.prototype.addAnnotation = function (annotation, url) {
      return ConcurrencySafeEntity.prototype.update.call(this,
                                                         url,
                                                         annotation.getServerAnnotation());
    };

    /**
     * The entity that includes this mixin needs to implement 'asyncCreate'.
     */
    HasAnnotations.prototype.removeAnnotation = function (annotation, url) {
      var self = this,
          found = _.find(self.annotations,  { annotationTypeId: annotation.annotationTypeId });

      if (!found) {
        return $q.reject('annotation with annotation type ID not present: ' + annotation.annotationTypeId);
      }

      return biobankApi.del(url).then(function () {
        return self.asyncCreate(
          _.extend(self, {
            version: self.version + 1,
            annotations: _.filter(self.annotations, function(at) {
              return at.uniqueId !== annotation.uniqueId;
            })
          }));
      });
    };

    HasAnnotations.prototype.setAnnotationTypes = function (annotationTypes) {
      var self = this,
          differentIds;

      self.annotations = self.annotations || [];

      // make sure the annotations ids match up with the corresponding annotation types
      differentIds = _.difference(_.map(self.annotations, 'annotationTypeId'),
                                  _.map(annotationTypes, 'uniqueId'));

      if (differentIds.length > 0) {
        throw new DomainError('annotation types not found: ' + differentIds);
      }

      self.annotations = _.map(annotationTypes, function (annotationType) {
        var jsonAnnotationMaybe = _.find(self.annotations,
                                     { annotationTypeId: annotationType.uniqueId });

        if (jsonAnnotationMaybe instanceof Annotation) {
          // annotation was already converted to Annotation or sub class
          return jsonAnnotationMaybe;
        }

        // undefined is valid input
        return annotationFactory.create(jsonAnnotationMaybe, annotationType);
      });
    };

    HasAnnotations.prototype.validAnnotations = function (annotations) {
      var result;

      if (_.isUndefined(annotations) || (annotations.length <= 0)) {
        // there are no annotation types, nothing to validate
        return true;
      }
      result = _.find(annotations, function (annot) {
        return !Annotation.isValid(annot);
      });

      return _.isUndefined(result);
    };

    return HasAnnotations;
  }

  return HasAnnotationsFactory;
});
