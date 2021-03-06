/**
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2015 Canadian BioSample Repository (CBSR)
 */
define(['moment', 'lodash', 'tv4'], function(moment, _, tv4) {
  'use strict';

  AnnotationFactory.$inject = [
    '$log',
    'AnnotationValueType',
    'DomainError'
  ];

  function AnnotationFactory($log, AnnotationValueType, DomainError) {

    var schema = {
      'id': 'Annotation',
      'type': 'object',
      'properties': {
        'annotationTypeId': { 'type': 'string' },
        'stringValue':      { 'type': [ 'string', 'null' ] },
        'numberValue':      { 'type': [ 'string', 'null' ] },
        'selectedValues':   { 'type': 'array' }
      },
      'required': [ 'annotationTypeId', 'selectedValues' ]
    };

    /**
     * Please use annotationFactory.create to create annotation objects.
     */
    function Annotation(obj, annotationType) {
      var self = this,
          defaults = {
            annotationTypeId: null,
            stringValue:      null,
            numberValue:      null,
            selectedValues:   []
          };

      obj = obj || {};
      _.extend(this, defaults, _.pick(obj, _.keys(defaults)));

      if (annotationType) {
        self.annotationTypeId = annotationType.uniqueId;
        self.annotationType = annotationType;

        if (!_.includes(_.values(AnnotationValueType), annotationType.valueType)) {
          throw new DomainError('value type is invalid: ' + annotationType.valueType);
        }

        if (_.isUndefined(annotationType.required)) {
          throw new DomainError('required not defined');
        }

        self.required = annotationType.required;

        if (annotationType.valueType === AnnotationValueType.SELECT) {
          if (!annotationType.isMultipleSelect() && !annotationType.isSingleSelect()) {
            throw new DomainError('invalid value for max count');
          }
        }
      }
    }

    Annotation.isValid = function (obj) {
      return tv4.validate(obj, schema);
    };

    Annotation.validAnnotations = function (annotations) {
      return _.reduce(
        annotations,
        function (memo, annotation) {
          return memo && tv4.validate(annotation, schema);
        },
        true);
    };

    Annotation.getInvalidError = function () {
      return tv4.error;
    };

    Annotation.create = function (obj) {
      if (!Annotation.isValid(obj)) {
        $log.error('invalid object to create from: ' + tv4.error);
        throw new DomainError('invalid object to create from: ' + tv4.error);
      }
      return new Annotation(obj);
    };

    /**
     *
     */
    Annotation.prototype.getAnnotationTypeId = function () {
      if (_.isUndefined(this.annotationType)) {
        throw new DomainError('annotation type not assigned');
      }
      return this.annotationType.uniqueId;
    };

    /**
     *
     */
    Annotation.prototype.getValueType = function () {
      if (_.isUndefined(this.annotationType)) {
        throw new DomainError('annotation type not assigned');
      }
      return this.annotationType.valueType;
    };

    Annotation.prototype.setValue = function (value) {
      this.value = value;
    };

    /**
     * Returns the label to display for the annotation.
     */
    Annotation.prototype.getLabel = function () {
      if (_.isUndefined(this.annotationType)) {
        throw new DomainError('annotation type not assigned');
      }
      return this.annotationType.name;
    };

    /**
     * For non requried annotation types, this always returns true. For required annotation types,
     * returns true if the value is not empty.
     */
    Annotation.prototype.isValueValid = function () {
      var value;

      if (!this.required) {
        return true;
      }

      value = this.getValue();
      if (_.isUndefined(value) || _.isNull(value)) {
        return false;
      }

      if (_.isString(value)) {
        value = value.trim();
        return (value !== '');
      }

      return (value !== null);
    };

    /** return constructor function */
    return Annotation;
  }

  return AnnotationFactory;
});
