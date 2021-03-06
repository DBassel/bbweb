/**
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2016 Canadian BioSample Repository (CBSR)
 */
define(function () {
  'use strict';

  /**
   * Used for directives that update annotations on a domain entity.
   *
   * The test suite "user context" needs the following members:
   *
   *   - this.element: the angular element that contains the directive.
   *
   *   - this.scope: the scope passed to the directive.
   *
   *   - this.controller: the controller associated with the directive.
   *
   * The context object needs the following attributes.
   *
   *   @param {function} context.createDirective - a function that creates the directive. This function can
   *   also create the members listed below. Called using "call" and with this being the test suite "user
   *   context" (i.e. this).
   *
   *   @param {string} context.controllerUpdateFuncName - the name of the function on the directive's
   *   controller that updates the annotation.
   *
   *   @param {string} context.modalInputFuncName - the name of the {modalInput} function that opens a modal
   *   and allows the user to change the value on the annotation.
   *
   *   @param {Object} context.entity - the domain entity that has the annotation to be updated.
   *
   *   @param {string} context.entityUpdateFuncName - the function on the domain entity used to update the
   *   annotation.
   *
   *   @param {domain.annotations.Annotation} context.annotation - the annotation to be updated.
   *
   *   @param {object} context.newValue - the new value to apply to the annotation.
   */
  function annotationUpdateSharedBehaviour(context) {

    describe('(shared)', function() {

      beforeEach(inject(function() {
        this.injectDependencies('modalInput', 'notificationsService');
      }));

      it('on update should invoke the update method on entity', function() {
        var modalInputDeferred = this.$q.defer();

        modalInputDeferred.resolve(context.newValue);

        spyOn(this.modalInput, context.modalInputFuncName)
          .and.returnValue({ result: modalInputDeferred.promise});
        spyOn(context.entity.prototype, context.entityUpdateFuncName)
          .and.returnValue(this.$q.when(context.entity));
        spyOn(this.notificationsService, 'success').and.returnValue(this.$q.when('OK'));

        context.createDirective.call(this);
        this.controller[context.controllerUpdateFuncName](context.annotation);
        this.scope.$digest();

        expect(context.entity.prototype[context.entityUpdateFuncName]).toHaveBeenCalled();
        expect(this.notificationsService.success).toHaveBeenCalled();
      });

      it('error message should be displayed when update fails', function() {
        var modalDeferred = this.$q.defer(),
            updateDeferred = this.$q.defer();

        modalDeferred.resolve(context.newValue);
        updateDeferred.reject('simulated error');

        spyOn(this.modalInput, context.modalInputFuncName)
          .and.returnValue({ result: modalDeferred.promise});
        spyOn(context.entity.prototype, context.entityUpdateFuncName)
          .and.returnValue(updateDeferred.promise);
        spyOn(this.notificationsService, 'updateError').and.returnValue(this.$q.when('OK'));

        context.createDirective.call(this);
        this.controller[context.controllerUpdateFuncName](context.annotation);
        this.scope.$digest();

        expect(this.notificationsService.updateError).toHaveBeenCalled();
      });

    });
  }

  return annotationUpdateSharedBehaviour;

});
