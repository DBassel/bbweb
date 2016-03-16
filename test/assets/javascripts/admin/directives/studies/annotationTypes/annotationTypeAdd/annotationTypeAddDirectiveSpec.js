/**
 * Jasmine test suite
 *
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2015 Canadian BioSample Repository (CBSR)
 */
define([
  'angular',
  'angularMocks',
  'underscore',
], function(angular, mocks, _) {
  'use strict';

  describe('Directive: annotationTypeAddDirective', function() {

    beforeEach(mocks.module('biobankApp', 'biobank.test'));

    beforeEach(inject(function ($rootScope, $compile, testUtils, jsonEntities, directiveTestSuite) {
      _.extend(this, directiveTestSuite);

      this.AnnotationType      = this.$injector.get('AnnotationType');
      this.AnnotationValueType = this.$injector.get('AnnotationValueType');
      this.jsonEntities        = this.$injector.get('jsonEntities');

      this.putHtmlTemplates(
        '/assets/javascripts/admin/directives/studies/annotationTypes/annotationTypeAdd/annotationTypeAdd.html');

      this.onSubmit = jasmine.createSpy('onSubmit');
      this.onCancel = jasmine.createSpy('onCancel');

      this.element = angular.element([
        '<annotation-type-add',
        '  on-submit="vm.onSubmit"',
        '  on-cancel="vm.onCancel()"',
        '</annotation-type-add>'
      ].join(''));

      this.scope = $rootScope.$new();
      this.scope.vm = {
        onSubmit: this.onSubmit,
        onCancel: this.onCancel
      };
      $compile(this.element)(this.scope);
      this.scope.$digest();
      this.controller = this.element.controller('annotationTypeAdd');
    }));

    it('scope should be valid when adding', function() {
      expect(this.controller.annotationType).toEqual(jasmine.any(this.AnnotationType));
      expect(this.controller.title).toBe('Add Annotation Type');
      expect(this.controller.valueTypes).toEqual(this.AnnotationValueType.values());
    });

    it('maxValueCountRequired is valid', function() {
      this.controller.annotationType.valueType = this.AnnotationValueType.SELECT();

      this.controller.annotationType.maxValueCount = 0;
      expect(this.controller.maxValueCountRequired()).toBe(true);

      this.controller.annotationType.maxValueCount = 3;
      expect(this.controller.maxValueCountRequired()).toBe(true);

      this.controller.annotationType.maxValueCount = 1;
      expect(this.controller.maxValueCountRequired()).toBe(false);

      this.controller.annotationType.maxValueCount = 2;
      expect(this.controller.maxValueCountRequired()).toBe(false);
    });

    it('calling valueTypeChange clears the options array', function() {
      this.controller.annotationType.valueType = 'Select';
      this.controller.annotationType.maxValueCount = 1;

      this.controller.valueTypeChange();
      expect(this.controller.annotationType.options).toBeArray();
      expect(this.controller.annotationType.options).toBeEmptyArray();
    });

    it('calling optionAdd appends to the options array', function() {
      this.controller.annotationType.valueType = 'Select';
      this.controller.annotationType.maxValueCount = 1;
      this.controller.annotationType.valueTypeChanged();

      this.controller.optionAdd();
      expect(this.controller.annotationType.options).toBeArrayOfSize(1);
      expect(this.controller.annotationType.options).toBeArrayOfStrings();
    });

    it('calling optionRemove throws an error on empty array', function() {
      this.controller.annotationType.valueType = 'Select';
      this.controller.annotationType.maxValueCount = 1;
      this.controller.annotationType.valueTypeChanged();
      expect(function () { this.controller.optionRemove('abc'); }).toThrow();
    });

    it('calling optionRemove throws an error if removal results in empty array', function() {
      this.controller.annotationType.valueType = 'Select';
      this.controller.annotationType.maxValueCount = 1;
      this.controller.annotationType.valueTypeChanged();
      this.controller.annotationType.options = ['abc'];
      expect(function () { this.controller.optionRemove('abc'); }).toThrow();
    });

    it('calling optionRemove removes an option', function() {
      // note: more than two strings in options array
      var options = ['abc', 'def'];
      this.controller.annotationType.valueType = 'Select';
      this.controller.annotationType.maxValueCount = 1;
      this.controller.annotationType.valueTypeChanged();
      this.controller.annotationType.options = options.slice(0);
      this.controller.optionRemove('abc');
      expect(this.controller.annotationType.options).toBeArrayOfSize(options.length - 1);
    });

    it('calling removeButtonDisabled returns valid results', function() {
      // note: more than two strings in options array
      var options = ['abc', 'def'];
      this.controller.annotationType.valueType = 'Select';
      this.controller.annotationType.maxValueCount = 1;
      this.controller.annotationType.valueTypeChanged();
      this.controller.annotationType.options = options.slice(0);

      expect(this.controller.removeButtonDisabled()).toEqual(false);

      this.controller.annotationType.options = options.slice(1);
      expect(this.controller.removeButtonDisabled()).toEqual(true);
    });

    it('should invoke submit function', function() {
      var annotType = new this.AnnotationType(this.jsonEntities.annotationType());
      this.controller.submit(annotType);
      expect(this.onSubmit).toHaveBeenCalledWith(annotType);
    });

    it('should invoke cancel function', function() {
      this.controller.cancel();
      expect(this.onCancel).toHaveBeenCalled();
    });

});

});
