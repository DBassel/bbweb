/**
 * User controllers.
 *
 * Allow for autofill / autocomplete. See the following web page for an explanation:
 *
 * http://timothy.userapp.io/post/63412334209/form-autocomplete-and-remember-password-with-angularjs
 */
define(['angular'], function(angular) {
  'use strict';

  var mod = angular.module('users.controllers', []);

  mod.controller('LoginCtrl', [
    '$scope', '$state', '$stateParams', '$location', '$log', 'stateHelper', 'userService', 'modalService',
    function($scope, $state, $stateParams, $location, $log, stateHelper, userService, modalService) {
      $scope.form = {
        credentials: {
          email: "",
          password: ""
        },
        login: function(credentials) {
          userService.login(credentials).then(
            function(user) {
              $location.path('/dashboard');
            },
            function(response) {
              // login failed
              var modalOptions = {
                closeButtonText: 'Cancel',
                actionButtonText: 'Retry',
                headerText: 'Invalid login credentials',
                bodyText: 'The email and / or password you entered are invalid.'
              };
              modalService.showModal({}, modalOptions).then(function (result) {
                stateHelper.reloadAndReinit();
              }, function () {
                $location.path('/');
              });
            });
        },
        forgotPassword: function() {
          $state.go("users.forgot");
        },
        register: function() {
          alert("register user");
        }
      };
    }]);

  mod.controller('ForgotPasswordCtrl', [
    '$scope', '$state', '$stateParams', '$log', 'userService', 'modalService', 'emailNotFound',
    function($scope, $state, $stateParams, $log, userService, modalService, emailNotFound) {
      $scope.form = {
        email: "",
        emailNotFound: emailNotFound,
        submit: function(email) {
          userService.passwordReset(email).then(
            function() {
              // password reset, email sent
              $state.go("users.forgot.passwordSent", { email: email });
            },
            function(response) {
              // user not found
              var status = response.status;
              if (status == 404) {
                $state.go("users.forgot.emailNotFound");
              } else {
                // user not active
                var modalDefaults = {
                  templateUrl: '/assets/javascripts/common/modalOk.html'
                };
                var modalOptions = {
                  headerText: 'Cannot reset your password',
                  bodyText: 'The account associated with that email is not active in the system. ' +
                    'Please contact your system administrator for more information.'
                };
                modalService.showModal(modalDefaults, modalOptions).then(function (result) {
                  $state.go("home");
                }, function () {
                  $state.go("home");
                });
              }
            });
        }
      };
    }]);

  mod.controller('ResetPasswordCtrl', [
    '$scope', '$state', '$stateParams', '$log', 'userService',
    function($scope, $state, $stateParams, $log, userService) {
      $scope.page = {
        email: $stateParams.email,
        login: function() {
          $state.go("users.login");
        }
      };
    }]);

});