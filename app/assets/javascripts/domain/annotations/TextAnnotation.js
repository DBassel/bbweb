/**
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2015 Canadian BioSample Repository (CBSR)
 */
define(function() {
  'use strict';

  TextAnnotationFactory.$inject = ['Annotation'];

  function TextAnnotationFactory(Annotation) {

    /**
     * Please use annotationFactory.create to create annotation objects.
     */
    function TextAnnotation(obj, annotationType) {
      obj = obj || {};
      Annotation.call(this, obj, annotationType);

      if (obj.stringValue) {
        this.value = obj.stringValue;
      }
      this.valueType = 'Text';
    }

    TextAnnotation.prototype = Object.create(Annotation.prototype);
    TextAnnotation.prototype.constructor = TextAnnotation;

    TextAnnotation.prototype.getValue = function () {
      return this.value;
    };

    TextAnnotation.prototype.getServerAnnotation = function () {
      return {
        annotationTypeId: this.getAnnotationTypeId(),
        stringValue:      this.value || '',
        selectedValues:   []
      };
    };

    return TextAnnotation;
  }

  return TextAnnotationFactory;
});
