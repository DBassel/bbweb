/**
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2016 Canadian BioSample Repository (CBSR)
 */
define(['underscore'], function (_) {
  'use strict';

  AnnotationsFactory.$inject = [ 'biobankApi', 'Annotation' ];

  function AnnotationsFactory(biobankApi, Annotation) {

    var mixins = {
      validAnnotations: validAnnotations,
      removeAnnotation: removeAnnotation
    };

    return mixins;

    //--

    function validAnnotations(annotations) {
      var result;

      if (_.isUndefined(annotations) || (annotations.length <= 0)) {
        // there are no annotation types, nothing to validate
        return true;
      }
      result = _.find(annotations, function (annot) {
        return !Annotation.isValid(annot);
      });

      return _.isUndefined(result);
    }

    /**
     * The entity that includes this mixin needs to implement 'asyncCreate'.
     */
    function removeAnnotation(annotation, url) {
      /* jshint validthis:true */
      var self = this,
          found = _.findWhere(self.annotations,  { annotationTypeId: annotation.annotationTypeId });

      if (!found) {
        throw new Error('annotation with annotation type ID not present: ' + annotation.annotationTypeId);
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

    }
  }

  return AnnotationsFactory;
});
