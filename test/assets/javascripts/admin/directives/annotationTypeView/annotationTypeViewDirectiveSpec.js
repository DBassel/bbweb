/**
 * Jasmine test suite
 *
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2016 Canadian BioSample Repository (CBSR)
 */
define([
  'angular',
  'angularMocks',
  'lodash',
  'biobankApp'
], function(angular, mocks, _) {
  'use strict';

  function SuiteMixinFactory(TestSuiteMixin) {

    function SuiteMixin() {
    }

    SuiteMixin.prototype = Object.create(TestSuiteMixin.prototype);
    SuiteMixin.prototype.constructor = SuiteMixin;

    SuiteMixin.prototype.createScope = function (options) {
      options = options || {};
      options.study = options.study || this.study;
      options.annotationType = options.annotationType || this.annotationType;

      this.element = angular.element([
        '<annotation-type-view ',
        '  study="vm.study"',
        '  annotation-type="vm.annotationType"',
        '  return-state="' + this.returnState  + '"',
        '  on-update="vm.onUpdate"',
        '</annotation-type-view>',
      ].join(''));

      this.scope = this.$rootScope.$new();
      this.scope.vm = {
        study:          options.study,
        annotationType: options.annotationType,
        returnState:    this.returnState,
        onUpdate:       this.onUpdate
      };

      this.$compile(this.element)(this.scope);
      this.scope.$digest();
      this.controller = this.element.controller('annotationTypeView');
    };

    return SuiteMixin;
  }

  describe('annotationTypeViewDirective', function() {

    beforeEach(mocks.module('biobankApp', 'biobank.test'));

    beforeEach(inject(function (TestSuiteMixin) {
      var SuiteMixin = new SuiteMixinFactory(TestSuiteMixin);
      _.extend(this, SuiteMixin.prototype);

      this.injectDependencies('$q',
                              '$rootScope',
                              '$compile',
                              '$state',
                              'Study',
                              'AnnotationType',
                              'AnnotationValueType',
                              'AnnotationMaxValueCount',
                              'modalInput',
                              'notificationsService',
                              'factory');

      this.putHtmlTemplates(
        '/assets/javascripts/admin/directives/annotationTypeView/annotationTypeView.html',
        '/assets/javascripts/common/directives/truncateToggle/truncateToggle.html',
        '/assets/javascripts/common/modalInput/modalInput.html');

      this.returnState      = 'my-return-state';
      this.study            = new this.Study(this.factory.study());
      this.annotationType   = new this.AnnotationType(this.factory.annotationType());
      this.onUpdate         = jasmine.createSpy('onUpdate').and.returnValue(this.$q.when(this.study));
    }));

    it('should have valid scope', function() {
      this.createScope();
      expect(this.controller.annotationType).toBe(this.annotationType);
      expect(this.controller.returnState).toBe(this.returnState);
      expect(this.controller.onUpdate).toBeFunction();
    });

    it('call to back function returns to valid state', function() {
      spyOn(this.$state, 'go').and.returnValue(0);

      this.createScope();
      this.controller.back();
      this.scope.$digest();
      expect(this.$state.go).toHaveBeenCalledWith(this.returnState, {}, { reload: true });
    });

    describe('updates to name', function () {

      var context = {};

      beforeEach(inject(function () {
        context.controllerFuncName = 'editName';
        context.modalInputFuncName = 'text';
      }));

      sharedUpdateBehaviour(context);

    });

    describe('updates to required', function () {

      var context = {};

      beforeEach(inject(function () {
        context.controllerFuncName = 'editRequired';
        context.modalInputFuncName = 'boolean';
      }));

      sharedUpdateBehaviour(context);

    });

    describe('updates to description', function () {

      var context = {};

      beforeEach(inject(function () {
        context.controllerFuncName = 'editDescription';
        context.modalInputFuncName = 'textArea';
      }));

      sharedUpdateBehaviour(context);

    });

    describe('updates to selections', function () {

      it('can edit selections on a single select', inject(function (annotationTypeUpdateModal) {
        var annotationType = new this.AnnotationType(
          this.factory.annotationType({
            valueType:     this.AnnotationValueType.SELECT,
            maxValueCount: this.AnnotationMaxValueCount.SELECT_SINGLE,
            options:       [ 'option1', 'option2' ],
            required:      true
          }));
        spyOn(annotationTypeUpdateModal, 'openModal').and.returnValue({ result: this.$q.when([]) });

        this.createScope({ study: undefined, annotationType: annotationType });
        this.controller.editSelectionOptions();
        this.scope.$digest();
        expect(annotationTypeUpdateModal.openModal).toHaveBeenCalled();
      }));

      it('an exception is thrown for annotation types that are not select', function () {
        var self = this,
            annotationTypes = _.chain(self.factory.allAnnotationTypes())
            .filter(function (json) {
              return (json.valueType !== self.AnnotationValueType.SELECT);
            })
            .map(function (json) {
              var result = new self.AnnotationType(json);
              return result;
            })
            .value();

        _.each(annotationTypes, function (annotationType) {
          expect(function () {
            self.createScope({ study: undefined, annotationType: annotationType });
            self.controller.editSelectionOptions();
          }).toThrowError(/invalid annotation type:/);
        });
      });

    });

    function sharedUpdateBehaviour(context) {

      describe('(shared) update functions', function () {

        it('should update a field', function() {
          var newValue = this.factory.stringNext();

          spyOn(this.modalInput, context.modalInputFuncName).and.returnValue(
            { result: this.$q.when(newValue)});

          this.createScope();
          expect(this.controller[context.controllerFuncName]).toBeFunction();
          this.controller[context.controllerFuncName]();
          this.scope.$digest();
          expect(this.onUpdate).toHaveBeenCalled();
        });

      });
    }
  });

});
