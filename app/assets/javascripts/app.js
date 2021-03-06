/**
 * The app module, as both AngularJS as well as RequireJS module.
 *
 * Splitting an app in several Angular modules serves no real purpose in Angular 1.0/1.1.
 * (Hopefully this will change in the near future.)
 *
 * Splitting it into several RequireJS modules allows async loading. We cannot take full advantage
 * of RequireJS and lazy-load stuff 'because the angular modules have their own dependency system.
 *
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2015 Canadian BioSample Repository (CBSR)
 */
define(function(require) {
  'use strict';

  var angular           = require('angular'),
      admin             = require('admin'),
      centres           = require('centres'),
      common            = require('common'),
      collection        = require('collection'),
      domain            = require('domain'),
      home              = require('home'),
      shipmentSpecimens = require('shipmentSpecimens'),
      studies           = require('studies'),
      users             = require('users'),
      AppConfig         = require('./AppConfig'),
      app;

  require('angular-animate');
  require('angular-cookies');
  require('angular-gettext');
  require('angular-messages');
  require('angular-sanitize');
  require('angular-toastr');
  require('angular-ui-router');
  require('angular-utils-ui-breadcrumbs');
  require('bootstrap');
  require('bootstrap-ui-datetime-picker');
  require('jquery');
  require('smart-table');
  require('ui-bootstrap');

  // We must already declare most dependencies here (except for common), or the submodules' routes
  // will not be resolved
  angular.module('biobankApp',
                 [
                   'angularUtils.directives.uiBreadcrumbs',
                   'gettext',
                   'ngAnimate',
                   'ngCookies',
                   'ngMessages',
                   'ngSanitize',
                   'smart-table',
                   'toastr',
                   'ui.bootstrap',
                   'ui.bootstrap.datetimepicker',
                   'ui.router',
                   admin.name,
                   centres.name,
                   common.name,
                   collection.name,
                   domain.name,
                   home.name,
                   shipmentSpecimens.name,
                   studies.name,
                   users.name
                 ])

    .run(['languageService', function (languageService) {
      languageService.initLanguage({ debug: true });
    }])

    .run(debugFunc) // For debugging
    .provider('AppConfig', AppConfig)

  // see http://blog.thoughtram.io/angularjs/2014/12/22/exploring-angular-1.3-disabling-debug-info.html
  //
  // app.config(['$compileProvider', function ($compileProvider) {
  //   $compileProvider.debugInfoEnabled(false);
  // }]);

  //.config(exceptionConfig)
    .config(loggingConfig)
    .config(httpInterceptorConfig)
    .config(configToastr);

  //--

  configToastr.$inject = ['toastrConfig'];
  function configToastr(toastrConfig) {
    angular.extend(toastrConfig, {
        positionClass: 'toast-bottom-right'
    });

  }

  debugFunc.$inject = ['$rootScope'];

  function debugFunc($rootScope) {
    /*jshint unused: false*/

    // change these to true to have the information displayed in the console
    var debugStateChangeStart   = false;
    var debugStateChangeSuccess = false;
    var debugViewContentLoading = false;
    var debugViewContentLoaded  = false;
    var debugStateNotFound      = false;

    // $rootScope.$state = $state;
    // $rootScope.$stateParams = $stateParams;

    $rootScope.$on('$stateChangeError',function(event, toState, toParams, fromState , fromParams){
      console.log('$stateChangeError - fired when an error occurs during transition.');
      console.log(arguments);
    });

    if (debugStateChangeStart) {
      $rootScope.$on('$stateChangeStart',function(event, toState, toParams, fromState, fromParams){
        console.log('$stateChangeStart to '+ toState.name + ' from ' + fromState.name +
                    ' - fired when the transition begins. toState, toParams : \n', toState, toParams);
      });
    }

    if (debugStateChangeSuccess) {
      $rootScope.$on('$stateChangeSuccess',function(event, toState, toParams, fromState, fromParams){
        console.log('$stateChangeSuccess to '+ toState.name +
                    '- fired once the state transition is complete.');
      });
    }

    if (debugViewContentLoading) {
      $rootScope.$on('$viewContentLoading',function(event, viewConfig){
        // runs on individual scopes, so putting it in 'run' doesn't work.
        console.log('$viewContentLoading - view begins loading - dom not rendered',viewConfig);
      });
    }

    if (debugViewContentLoaded) {
      $rootScope.$on('$viewContentLoaded',function(event){
        console.log('$viewContentLoaded - fired after dom rendered',event);
      });
    }

    if (debugStateNotFound) {
      $rootScope.$on('$stateNotFound',function(event, unfoundState, fromState, fromParams){
        console.log('$stateNotFound '+ unfoundState.to +
                    '  - fired when a state cannot be found by its name.');
        console.log(unfoundState, fromState, fromParams);
      });
    }

    /*jshint unused: true*/
  }

  // exceptionConfig.$inject = ['$provide'];

  // function exceptionConfig($provide) {
  //   $provide.decorator('$exceptionHandler', extendExceptionHandler);
  // }

  // extendExceptionHandler.$inject = ['$delegate'];

  // function extendExceptionHandler($delegate) {
  //   return function (exception, cause) {
  //     $delegate(exception, cause);
  //     // var errorData = {
  //     //   exception: exception,
  //     //   cause: cause
  //     // };

  //     /**
  //      * Could add the error to a service's collection,
  //      * add errors to $rootScope, log errors to remote web server,
  //      * or log locally. Or throw hard. It is entirely up to you.
  //      * throw exception;
  //      */
  //     toastr.error(
  //       exception.message,
  //       'Exception',
  //       { positionClass: 'toast-bottom-right' });
  //   };
  // }

  loggingConfig.$inject = ['$logProvider'];

  function loggingConfig($logProvider) {
    $logProvider.debugEnabled(true);
  }

  httpInterceptorConfig.$inject = ['$httpProvider'];

  function httpInterceptorConfig($httpProvider) {

    $httpProvider.interceptors.push(httpInterceptor);

    //---

    httpInterceptor.$inject = ['$q', '$timeout', '$injector'];

    function httpInterceptor($q, $timeout, $injector) {
      return { 'responseError': responseError };

      //--

      function responseError(rejection) {
        if (rejection.status === 401) {
          $injector.get('usersService').sessionTimeout();
          $injector.get('$state').go('home.users.login', {}, { reload: true });
          $injector.get('$log').info('your session has timed out');
        }
        return $q.reject(rejection);
      }
    }
  }

  return app;
});
