/**
 * @author Nelson Loyola <loyola@ualberta.ca>
 * @copyright 2016 Canadian BioSample Repository (CBSR)
 */
define(function () {
  'use strict';

  var component = {
    templateUrl : '/assets/javascripts/collection/components/ceventSpecimensView/ceventSpecimensView.html',
    controller: CeventSpecimensViewController,
    controllerAs: 'vm',
    bindings: {
      study:           '<',
      collectionEvent: '<'
    }
  };

  CeventSpecimensViewController.$inject = [
    '$q',
    '$state',
    'gettextCatalog',
    'Specimen',
    'Centre',
    'specimenAddModal',
    'domainNotificationService',
    'notificationsService'
  ];

  /**
   *
   */
  function CeventSpecimensViewController($q,
                                         $state,
                                         gettextCatalog,
                                         Specimen,
                                         Centre,
                                         specimenAddModal,
                                         domainNotificationService,
                                         notificationsService) {
    var vm = this;

    vm.specimens       = [];
    vm.centreLocations = [];
    vm.tableController = undefined;

    vm.addSpecimens    = addSpecimens;
    vm.getTableData    = getTableData;
    vm.removeSpecimen  = removeSpecimen;
    vm.viewSpecimen    = viewSpecimen;

    //--

    function addSpecimens() {
      var defer = $q.defer();

      if (vm.centreLocations.length <= 0) {
        vm.study.allLocations().then(function (centreLocations) {
          defer.resolve(Centre.centreLocationToNames(centreLocations));
        });
      } else {
        defer.resolve(vm.centreLocations);
      }

      defer.promise
        .then(function (centreLocations) {
          vm.centreLocations = centreLocations;
          return specimenAddModal.open(vm.centreLocations,
                                       vm.collectionEvent.collectionEventType.specimenSpecs,
                                       new Date(vm.collectionEvent.timeCompleted)).result;
        })
        .then(function (specimens) {
          return Specimen.add(vm.collectionEvent.id, specimens);
        })
        .then(function () {
          notificationsService.success(gettextCatalog.getString('Specimen added'));
          reloadTableData();
        });
    }

    function getTableData(tableState, controller) {
      var pagination    = tableState.pagination,
          sortPredicate = tableState.sort.predicate || 'inventoryId',
          sortOrder     = tableState.sort.reverse || false,
          options = {
            sort:     sortPredicate,
            page:     1 + (pagination.start / vm.limit),
            limit: vm.limit,
            order:    sortOrder ? 'desc' : 'asc'
          };

      if (!vm.tableController && controller) {
        vm.tableController = controller;
      }

      vm.tableDataLoading = true;

      Specimen.list(vm.collectionEvent.id, options).then(function (paginatedSpecimens) {
        vm.specimens = paginatedSpecimens.items;
        tableState.pagination.numberOfPages = paginatedSpecimens.maxPages;
        vm.tableDataLoading = false;
      });
    }

    function reloadTableData() {
      getTableData(vm.tableController.tableState());
    }

    function viewSpecimen(specimen) {
      $state.go('home.collection.study.participant.cevents.details.specimen',
                { inventoryId: specimen.inventoryId });
    }

    function removeSpecimen(specimen) {
      domainNotificationService.removeEntity(
        promiseFn,
        gettextCatalog.getString('Remove specimen'),
        gettextCatalog.getString(
          'Are you sure you want to remove specimen with inventory ID <strong>{{id}}</strong>?',
          { id: specimen.inventoryId }),
        gettextCatalog.getString('Remove failed'),
        gettextCatalog.getString(
          'Specimen with ID {{id}} cannot be removed',
          { id: specimen.inventoryId }));

      function promiseFn() {
        return specimen.remove().then(function () {
          notificationsService.success(gettextCatalog.getString('Specimen removed'));
          reloadTableData();
        });
      }
    }

  }

  return component;
});
