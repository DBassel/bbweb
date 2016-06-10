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
  'biobankApp'
], function(angular, mocks, _) {
  'use strict';

  describe('Directive: locationsPanelDirective', function() {

    beforeEach(mocks.module('biobankApp', 'biobank.test'));

    beforeEach(inject(function($q, $rootScope, $compile, templateMixin, testUtils) {
      var self = this;

      _.extend(self, templateMixin);

      self.$q                  = this.$injector.get('$q');
      self.Centre              = self.$injector.get('Centre');
      self.Location            = self.$injector.get('Location');
      self.modalService        = self.$injector.get('modalService');
      self.domainEntityService = self.$injector.get('domainEntityService');
      self.factory        = self.$injector.get('factory');
      self.createEntities      = createEntities;
      self.createController    = createController;

      testUtils.addCustomMatchers();

      self.putHtmlTemplates(
        '/assets/javascripts/admin/centres/directives/locationsPanel/locationsPanel.html',
        '/assets/javascripts/common/directives/panelButtons.html',
        '/assets/javascripts/common/directives/updateRemoveButtons.html');

      function createEntities() {
        var entities = {};

        entities.centre = new self.Centre(self.factory.centre());
        entities.locations = _.map(_.range(3), function () {
          return new self.Location(self.factory.location());
        });
        return entities;
      }

      function createController(centre) {
        var element = angular.element('<locations-panel centre="vm.centre"></locations-panel>');
        self.scope = $rootScope.$new();
        self.scope.vm = { centre: centre };
        $compile(element)(self.scope);
        self.scope.$digest();
        self.controller = element.controller('locationsPanel');
      }
    }));

    it('has valid scope', function() {
      var entities = this.createEntities();
      this.createController(entities.centre);
      expect(this.controller.centre).toBe(entities.centre);
    });

    it('can add a location', function() {
      var $state = this.$injector.get('$state'),
          entities = this.createEntities();

      this.createController(entities.centre);
      spyOn($state, 'go').and.callFake(function () {});
      this.controller.add();
      this.scope.$digest();
      expect($state.go).toHaveBeenCalledWith(
        'home.admin.centres.centre.locations.locationAdd',
        {},
        { reload: true });
    });

    it('can remove a location', function() {
      var entities            = this.createEntities(),
          locationToRemove    = entities.locations[0];

      entities.centre.locations.push(locationToRemove);
      this.createController(entities.centre);

      spyOn(this.modalService, 'showModal').and.returnValue(this.$q.when('OK'));
      spyOn(this.Centre.prototype, 'removeLocation').and.returnValue(this.$q.when(entities.centre));

      this.controller.remove(locationToRemove);
      this.scope.$digest();
      expect(this.Centre.prototype.removeLocation).toHaveBeenCalled();
    });

    it('displays information modal when removal of a location fails', function() {
      var entities            = this.createEntities(),
          locationToRemove    = entities.locations[0],
          deferred            = this.$q.defer();

      deferred.reject('simulated remove error');
      entities.centre.locations.push(locationToRemove);

      spyOn(this.modalService, 'showModal').and.returnValue(this.$q.when('OK'));
      spyOn(this.Centre.prototype, 'removeLocation').and.returnValue(deferred.promise);

      this.createController(entities.centre);
      this.controller.remove(locationToRemove);
      this.scope.$digest();
      expect(this.modalService.showModal.calls.count()).toBe(2);
    });

  });

});