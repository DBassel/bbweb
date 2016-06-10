/**
 * Jasmine test suite
 */
define(function(require) {
  'use strict';

  var angular                              = require('angular'),
      mocks                                = require('angularMocks'),
      _                                    = require('underscore'),
      annotationTypeAddDirectiveSharedSpec = require('../../../../directives/annotationTypeAddDirectiveSharedSpec');

  describe('Directive: collectionEventAnnotationTypeAddDirective', function() {

    beforeEach(mocks.module('biobankApp', 'biobank.test'));

    beforeEach(inject(function($rootScope, $compile, templateMixin, testUtils) {
      var self = this;

      _.extend(self, templateMixin);

      self.CollectionEventType = self.$injector.get('CollectionEventType');
      self.AnnotationType      = self.$injector.get('AnnotationType');
      self.factory        = self.$injector.get('factory');

      self.collectionEventType = new self.CollectionEventType(
        self.factory.collectionEventType(self.factory.study()));
      self.createController = createController;

      self.putHtmlTemplates(
        '/assets/javascripts/admin/studies/directives/annotationTypes/collectionEventAnnotationTypeAdd/collectionEventAnnotationTypeAdd.html',
        '/assets/javascripts/admin/directives/annotationTypeAdd/annotationTypeAdd.html');

      function createController() {
        self.element = angular.element([
          '<collection-event-annotation-type-add',
          '  collection-event-type="vm.ceventType">',
          '</collection-event-annotation-type-add>'
        ].join(''));

        self.scope = $rootScope.$new();
        self.scope.vm = { ceventType: self.collectionEventType };
        $compile(self.element)(self.scope);
        self.scope.$digest();
        self.controller = self.element.controller('collectionEventAnnotationTypeAdd');
      }
    }));

    it('should have  valid scope', function() {
      this.createController();
      expect(this.controller.collectionEventType).toBe(this.collectionEventType);
    });

    describe('for onSubmit and onCancel', function () {
      var context = {};

      beforeEach(inject(function () {
        context.createController          = this.createController;
        context.scope                     = this.scope;
        context.controller                = this.controller;
        context.entity                    = this.CollectionEventType;
        context.addAnnotationTypeFuncName = 'addAnnotationType';
        context.returnState               = 'home.admin.studies.study.collection.ceventType';
      }));

      annotationTypeAddDirectiveSharedSpec(context);
    });

  });

});