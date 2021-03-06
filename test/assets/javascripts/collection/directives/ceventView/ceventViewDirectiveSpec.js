/**
 * Jasmine test suite
 *
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2016 Canadian BioSample Repository (CBSR)
 */
define(function(require) {
  'use strict';

  var angular                         = require('angular'),
      mocks                           = require('angularMocks'),
      _                               = require('lodash'),
      faker                           = require('faker'),
      annotationUpdateSharedBehaviour = require('../../../test/annotationUpdateSharedBehaviour');

  function SuiteMixinFactory(TestSuiteMixin) {

    function SuiteMixin() {
      TestSuiteMixin.call(this);
    }

    SuiteMixin.prototype = Object.create(TestSuiteMixin.prototype);
    SuiteMixin.prototype.constructor = SuiteMixin;

    SuiteMixin.prototype.collectionEventWithAnnotation = function (valueType, maxValueCount) {
      var jsonAnnotationType,
          value,
          jsonAnnotation,
          jsonCeventType,
          jsonCevent,
          collectionEventType;

      maxValueCount = maxValueCount || 0;

      jsonAnnotationType = this.factory.annotationType({ valueType: valueType,
                                                         maxValueCount: maxValueCount });
      value              = this.factory.valueForAnnotation(jsonAnnotationType);
      jsonAnnotation     = this.factory.annotation({ value: value }, jsonAnnotationType);
      jsonCeventType     = this.factory.collectionEventType({ annotationTypes: [ jsonAnnotationType ]});
      jsonCevent         = this.factory.collectionEvent({ annotations: [ jsonAnnotation ]});
      collectionEventType = new this.CollectionEventType(jsonCeventType);
      return new this.CollectionEvent(jsonCevent, collectionEventType);
    };

    SuiteMixin.prototype.createDirective = function (collectionEventTypes, collectionEvent) {
      collectionEventTypes = collectionEventTypes || this.collectionEventTypes;
      collectionEvent = collectionEvent || this.collectionEvent;

      this.element = angular.element([
        '<cevent-view',
        '  collection-event-types="vm.collectionEventTypes"',
        '  collection-event="vm.collectionEvent">',
        '</cevent-view>'
      ].join(''));

      this.scope = this.$rootScope.$new();
      this.scope.vm = {
        collectionEventTypes: collectionEventTypes,
        collectionEvent:      collectionEvent
      };
      this.$compile(this.element)(this.scope);
      this.scope.$digest();
      this.controller = this.element.controller('ceventView');
    };

    return SuiteMixin;
  }

  describe('directive: ceventViewDirective', function() {

    mocks.module.sharedInjector();

    beforeAll(mocks.module('biobankApp', 'biobank.test'));

    beforeEach(inject(function(TestSuiteMixin) {
      var SuiteMixin = new SuiteMixinFactory(TestSuiteMixin);
      _.extend(this, SuiteMixin.prototype);

      this.injectDependencies('$rootScope',
                              '$compile',
                              '$q',
                              '$state',
                              'Participant',
                              'CollectionEvent',
                              'CollectionEventType',
                              'AnnotationValueType',
                              'AnnotationMaxValueCount',
                              'Specimen',
                              'domainNotificationService',
                              'modalService',
                              'notificationsService',
                              'factory');

      this.putHtmlTemplates(
        '/assets/javascripts/collection/directives/ceventView/ceventView.html',
        '/assets/javascripts/collection/components/ceventSpecimensView/ceventSpecimensView.html',
        '/assets/javascripts/common/directives/statusLine/statusLine.html',
        '/assets/javascripts/common/directives/pagination.html');

      this.jsonCevent      = this.factory.collectionEvent();
      this.jsonParticipant = this.factory.defaultParticipant();
      this.jsonCeventType  = this.factory.defaultCollectionEventType();

      this.participant          = new this.Participant(this.jsonParticipant);
      this.collectionEvent      = new this.CollectionEvent(this.jsonCevent);
      this.pagedResult          = this.factory.pagedResult([ this.collectionEvent ]);
      this.collectionEventTypes = [ new this.CollectionEventType(this.jsonCeventType) ];
    }));

    it('has valid scope', function() {
      this.createDirective();

      expect(this.controller.collectionEventTypes).toBe(this.collectionEventTypes);
      expect(this.controller.collectionEvent).toBe(this.collectionEvent);
      expect(this.controller.panelOpen).toBeTrue();

      expect(this.controller.editTimeCompleted).toBeFunction();
      expect(this.controller.editAnnotation).toBeFunction();
      expect(this.controller.panelButtonClicked).toBeFunction();
    });

    it('panel can be closed and opened', function() {
      this.createDirective();

      this.controller.panelButtonClicked();
      this.scope.$digest();
      expect(this.controller.panelOpen).toBeFalse();

      this.controller.panelButtonClicked();
      this.scope.$digest();
      expect(this.controller.panelOpen).toBeTrue();
    });

    describe('updates to time completed', function () {

      var context = {};

      beforeEach(inject(function () {
        context.controllerUpdateFuncName = 'editTimeCompleted';
        context.modalInputFuncName       = 'dateTime';
        context.ceventUpdateFuncName     = 'updateTimeCompleted';
        context.collectionEvent          = this.collectionEvent;
        context.newValue                 = faker.date.recent(10);
      }));

      sharedUpdateBehaviour(context);

    });

    describe('updates to annotations', function () {

      var context = {};

      beforeEach(inject(function () {
        context.entity                   = this.CollectionEvent;
        context.entityUpdateFuncName     = 'addAnnotation';
      }));

      describe('updates to a text annotation', function () {

        beforeEach(inject(function () {
          var self = this,
              collectionEvent = this.collectionEventWithAnnotation(this.AnnotationValueType.TEXT);

          context.createDirective          = createDirective;
          context.controllerUpdateFuncName = 'editAnnotation';
          context.modalInputFuncName       = 'text';
          context.annotation               = collectionEvent.annotations[0];
          context.newValue                 = faker.random.word();

          function createDirective() {
            return self.createDirective(self.collectionEventTypes, collectionEvent);
          }
        }));

        annotationUpdateSharedBehaviour(context);

      });

      describe('updates to a date time annotation', function () {

        beforeEach(inject(function () {
          var self = this,
              newValue = faker.date.recent(10),
              collectionEvent = this.collectionEventWithAnnotation(this.AnnotationValueType.DATE_TIME);

          context.createDirective          = createDirective;
          context.controllerUpdateFuncName = 'editAnnotation';
          context.modalInputFuncName       = 'dateTime';
          context.annotation               = collectionEvent.annotations[0];
          context.newValue                 = { date: newValue, time: newValue };

          function createDirective() {
            return self.createDirective(self.collectionEventTypes, collectionEvent);
          }
        }));

        annotationUpdateSharedBehaviour(context);

      });

      describe('updates to a number annotation', function () {

        beforeEach(inject(function () {
          var self = this,
              collectionEvent = this.collectionEventWithAnnotation(this.AnnotationValueType.NUMBER);

          context.createDirective          = createDirective;
          context.controllerUpdateFuncName = 'editAnnotation';
          context.modalInputFuncName       = 'number';
          context.annotation               = collectionEvent.annotations[0];
          context.newValue                 = 10;

          function createDirective() {
            return self.createDirective(self.collectionEventTypes, collectionEvent);
          }
        }));

        annotationUpdateSharedBehaviour(context);

      });

      describe('updates to a single select annotation', function () {

        beforeEach(inject(function () {
          var self = this,
              collectionEvent = this.collectionEventWithAnnotation(this.AnnotationValueType.SELECT,
                                                                   this.AnnotationMaxValueCount.SELECT_SINGLE);

          context.createDirective          = createDirective;
          context.controllerUpdateFuncName = 'editAnnotation';
          context.modalInputFuncName       = 'select';
          context.annotation               = collectionEvent.annotations[0];
          context.newValue                 = collectionEvent.annotations[0].annotationType.options[0];

          function createDirective() {
            return self.createDirective(self.collectionEventTypes, collectionEvent);
          }
        }));

        annotationUpdateSharedBehaviour(context);

      });

      describe('updates to a multiple select annotation', function () {

        beforeEach(inject(function () {
          var self = this,
              collectionEvent = this.collectionEventWithAnnotation(this.AnnotationValueType.SELECT,
                                                                   this.AnnotationMaxValueCount.SELECT_MULTIPLE);

          context.createDirective          = createDirective;
          context.controllerUpdateFuncName = 'editAnnotation';
          context.modalInputFuncName       = 'selectMultiple';
          context.annotation               = collectionEvent.annotations[0];
          context.newValue                 = collectionEvent.annotations[0].annotationType.options;

          function createDirective() {
            return self.createDirective(self.collectionEventTypes, collectionEvent);
          }
        }));

        annotationUpdateSharedBehaviour(context);

      });

    });

    function sharedUpdateBehaviour(context) {

      describe('(shared) tests', function() {

        beforeEach(inject(function() {
          this.injectDependencies('CollectionEvent',
                                  'modalInput',
                                  'notificationsService');
        }));


        it('on update should invoke the update method on entity', function() {
          spyOn(this.modalInput, context.modalInputFuncName)
            .and.returnValue({ result: this.$q.when(context.newValue )});
          spyOn(this.CollectionEvent.prototype, context.ceventUpdateFuncName)
            .and.returnValue(this.$q.when(context.collectionEvent));
          spyOn(this.notificationsService, 'success').and.returnValue(this.$q.when('OK'));

          this.createDirective();
          this.controller[context.controllerUpdateFuncName]();
          this.scope.$digest();

          expect(this.CollectionEvent.prototype[context.ceventUpdateFuncName]).toHaveBeenCalled();
          expect(this.notificationsService.success).toHaveBeenCalled();
        });

        it('error message should be displayed when update fails', function() {
          spyOn(this.modalInput, context.modalInputFuncName)
            .and.returnValue({ result: this.$q.when(context.newValue )});
          spyOn(this.CollectionEvent.prototype, context.ceventUpdateFuncName)
            .and.returnValue(this.$q.reject('simulated error'));
          spyOn(this.notificationsService, 'updateError').and.returnValue(this.$q.when('OK'));

          this.createDirective();
          this.controller[context.controllerUpdateFuncName]();
          this.scope.$digest();

          expect(this.notificationsService.updateError).toHaveBeenCalled();
        });

      });
    }

    describe('when removing a collection event', function() {

      beforeEach(function() {
        this.collectionEvent = this.collectionEventWithAnnotation(this.AnnotationValueType.NUMBER);
      });

      it('can remove the collection event when cevent has no specimens', function() {
        this.Specimen.list =
          jasmine.createSpy('list').and.returnValue(this.$q.when({ items: [] }));

        this.modalService.modalOkCancel =
          jasmine.createSpy('modalOkCancel').and.returnValue(this.$q.when('OK'));

        this.CollectionEvent.prototype.remove =
          jasmine.createSpy('remove').and.returnValue(this.$q.when(this.collectionEvent));

        this.notificationsService.success =
          jasmine.createSpy('remove').and.returnValue(this.$q.when(null));

        this.$state.go =
          jasmine.createSpy('state.go').and.returnValue(null);

        this.createDirective();
        this.controller.remove();
        this.scope.$digest();
        expect(this.CollectionEvent.prototype.remove).toHaveBeenCalled();
        expect(this.notificationsService.success).toHaveBeenCalled();
        expect(this.modalService.modalOkCancel.calls.count()).toBe(1);
      });

      it('cannot remove the collection event due to server error', function() {
        this.Specimen.list =
          jasmine.createSpy('list').and.returnValue(this.$q.when({ items: [] }));

        this.modalService.modalOkCancel =
          jasmine.createSpy('modalOkCancel').and.returnValue(this.$q.when('OK'));

        this.CollectionEvent.prototype.remove =
          jasmine.createSpy('remove').and.returnValue(this.$q.reject('simulated error'));

        this.notificationsService.success =
          jasmine.createSpy('remove').and.returnValue(this.$q.when(null));

        this.$state.go =
          jasmine.createSpy('state.go').and.returnValue(null);

        this.createDirective();
        this.controller.remove();
        this.scope.$digest();
        expect(this.CollectionEvent.prototype.remove).toHaveBeenCalled();
        expect(this.notificationsService.success).not.toHaveBeenCalled();
        expect(this.modalService.modalOkCancel.calls.count()).toBe(2);
      });

      it('can NOT remove the collection event when cevent HAS specimens', function() {
        var specimen = new this.Specimen(this.factory.specimen());
        this.Specimen.list =
          jasmine.createSpy('list').and.returnValue(this.$q.when({ items: [ specimen ] }));

        this.modalService.modalOk =
          jasmine.createSpy('modalOk').and.returnValue(this.$q.when('OK'));

        this.createDirective();
        this.controller.remove();
        this.scope.$digest();
        expect(this.modalService.modalOk).toHaveBeenCalled();
      });

    });

    xit('should allow to edit  the visit type', function() {
      fail('this test should be written when the functionality is implemented');
    });


  });

});
