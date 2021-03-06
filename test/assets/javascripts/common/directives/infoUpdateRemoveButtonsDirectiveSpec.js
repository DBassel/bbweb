/**
 * Jasmine test suite
 *
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2015 Canadian BioSample Repository (CBSR)
 */
define([
  'angular',
  'angularMocks',
  'lodash',
  'biobankApp'
], function(angular, mocks, _) {
  'use strict';

  describe('Directive: infoUpdateRemoveButtons', function() {
    var buttonClickFuncNames = ['information', 'update', 'remove'];

    var createScope = function (options) {
      var self = this;

      self.scope = self.$rootScope.$new();

      options = options || {};

      self.scope.model = {};
      self.scope.model.updateAllowed = options.updateAllowed || false;
      self.scope.model.removeAllowed = options.removeAllowed || false;

      _.each(buttonClickFuncNames, function (key){
        self.scope.model[key] = function () {};
        spyOn(self.scope.model, key).and.returnValue(key);
      });

      self.$compile(self.element)(self.scope);
      self.scope.$digest();
    };

    beforeEach(mocks.module('biobankApp', 'biobank.test'));

    beforeEach(inject(function (TestSuiteMixin, testUtils) {
      _.extend(this, TestSuiteMixin.prototype);

      this.injectDependencies('$rootScope', '$compile');

      this.putHtmlTemplates(
        '/assets/javascripts/common/directives/infoUpdateRemoveButtons.html');

      this.element  = angular.element(
        '<info-update-remove-buttons' +
          '   on-info="model.information()"' +
          '   on-update="model.update()"' +
          '   on-remove="model.remove()"' +
          '   update-button-enabled="model.updateAllowed"' +
          '   remove-button-enabled="model.removeAllowed">' +
          '</info-update-remove-buttons>');
    }));

    it('clicking on a button invokes corresponding function', function() {
      var self = this, buttons;

      createScope.call(self, { updateAllowed: true, removeAllowed: true});

      buttons = self.element.find('button');

      expect(buttons.length).toBe(3);
      _.each(_.range(buttons.length), function (i) {
        buttons.eq(i).click();
        switch (i) {
        case 0: expect(self.scope.model.information).toHaveBeenCalled(); break;
        case 1: expect(self.scope.model.update).toHaveBeenCalled(); break;
        case 2: expect(self.scope.model.remove).toHaveBeenCalled(); break;
        }
      });
    });

    it('only one button displayed if updateAllowed and removeAllowed are false', function() {
      var buttons;

      createScope.call(this, { updateAllowed: false, removeAllowed: false});

      buttons = this.element.find('button');
      expect(buttons.length).toBe(1);
      buttons.eq(0).click();

      expect(this.scope.model.information).toHaveBeenCalled();
      expect(this.scope.model.update).not.toHaveBeenCalled();
      expect(this.scope.model.remove).not.toHaveBeenCalled();
    });

    it('buttons should have valid icons', function() {
      var icons;

      createScope.call(this, { updateAllowed: true, removeAllowed: true});
      icons = this.element.find('button i');

      expect(icons.length).toBe(3);
      expect(icons.eq(0)).toHaveClass('glyphicon-info-sign');
      expect(icons.eq(1)).toHaveClass('glyphicon-edit');
      expect(icons.eq(2)).toHaveClass('glyphicon-remove');
    });

    it('buttons should have valid titles', function() {
      var buttons;

      createScope.call(this, { updateAllowed: true, removeAllowed: true});
      buttons = this.element.find('button');

      expect(buttons.length).toBe(3);
      expect(buttons.eq(0).attr('title')).toBe('More information');
      expect(buttons.eq(1).attr('title')).toBe('Update');
      expect(buttons.eq(2).attr('title')).toBe('Remove');
    });

  });

});
