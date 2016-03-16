/**
 * Test module
 *
 * Provides factories for various client side domain objects.
 *
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2015 Canadian BioSample Repository (CBSR)
 */
define(function (require) {
  'use strict';

  var angular = require('angular');

  var module = angular.module('biobank.test', []);

  module.service('extendedDomainEntities', require('../../../test/assets/javascripts/test/extendedDomainEntities'));
  module.service('jsonEntities',           require('../../../test/assets/javascripts/test/jsonEntities'));
  module.service('testUtils',              require('../../../test/assets/javascripts/test/testUtils'));

  module.factory('entityTestSuite',    require('../../../test/assets/javascripts/test/entityTestSuite'));
  module.factory('directiveTestSuite', require('../../../test/assets/javascripts/test/directiveTestSuite'));

  return module;
});
