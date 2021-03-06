/**
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2015 Canadian BioSample Repository (CBSR)
 */
define(function () {
  'use strict';

  /**
   * The statuses a {@link domain.centres.Centre Centre} can have.
   *
   * @enum {string}
   * @memberOf domain.centres
   */
  var CentreState = {
    DISABLED: 'disabled',
    ENABLED:  'enabled'
  };

  return CentreState;
});
