import {
  createActions,
  combineActions,
  handleActions,
} from 'redux-actions';
import * as JsSearch from 'js-search';

import { mapObject } from 'lib/treeUtils';
import { actionCreators as docRefTypeActionCreators } from 'components/DocRefTypes';

const { docRefTypesReceived } = docRefTypeActionCreators;

const actionCreators = createActions({
  DOC_REF_LISTING_MOUNTED: (
    listingId,
    docRefs,
    alwaysFilter,
    allowMultiSelect,
    fixedDocRefTypeFilters,
  ) => ({
    listingId,
    docRefs,
    alwaysFilter,
    allowMultiSelect,
    fixedDocRefTypeFilters,
  }),
  DOC_REF_LISTING_UNMOUNTED: listingId => ({
    listingId,
    selectedItem: 0,
  }),
  FILTER_TERM_UPDATED: (listingId, filterTerm) => ({ listingId, filterTerm }),
  DOC_REF_TYPE_FILTER_UPDATED: (listingId, docRefTypeFilters) => ({ listingId, docRefTypeFilters }),
  DOC_REF_SELECTION_UP: listingId => ({ listingId, selectionChange: -1 }),
  DOC_REF_SELECTION_DOWN: listingId => ({ listingId, selectionChange: +1 }),
  DOC_REF_CHECK_TOGGLED: (listingId, uuid) => ({ listingId, uuid }),
});

const {
  docRefListingMounted,
  docRefListingUnmounted,
  filterTermUpdated,
  docRefSelectionUp,
  docRefSelectionDown,
  docRefTypeFilterUpdated,
} = actionCreators;

const defaultStatePerListing = {
  docRefs: [],
  filteredDocRefs: [],
  docRefTypeFilters: [],
  docRefTypesReceived: false,
  filterTerm: '',
  selectedItem: -1, // Used for simple item selection, by array index
  checkedDocRefUuids: [],
  inMultiSelectMode: false,
  search: undefined,
};

const defaultState = {};

const reducer = handleActions(
  {
    [docRefTypesReceived]: (state, { payload: { docRefTypes } }) =>
      mapObject(state, l => ({ ...l, docRefTypeFilters: docRefTypes, docRefTypesReceived: true })),
    [combineActions(
      docRefTypeFilterUpdated,
      docRefListingMounted,
      docRefListingUnmounted,
      filterTermUpdated,
    )]: (state, action) => {
      const {
        payload: {
          listingId,
          docRefs,
          alwaysFilter,
          filterTerm,
          docRefTypeFilters,
          allowMultiSelect,
          fixedDocRefTypeFilters,
        },
      } = action;
      const listingState = state[listingId] || defaultStatePerListing;

      const fixedDocRefTypeFiltersToUse =
        fixedDocRefTypeFilters !== undefined
          ? fixedDocRefTypeFilters
          : listingState.fixedDocRefTypeFilters;
      const allowMultiSelectToUse =
        allowMultiSelect !== undefined ? allowMultiSelect : listingState.allowMultiSelect;
      const alwaysFilterToUse =
        alwaysFilter !== undefined ? alwaysFilter : listingState.alwaysFilter;
      const filterTermToUse = filterTerm !== undefined ? filterTerm : listingState.filterTerm;
      const docRefsToUse = docRefs !== undefined ? docRefs : listingState.docRefs;
      const docRefTypeFiltersToUse =
        fixedDocRefTypeFiltersToUse.length > 0
          ? fixedDocRefTypeFiltersToUse
          : docRefTypeFilters !== undefined
            ? docRefTypeFilters
            : listingState.docRefTypeFilters;

      let filteredDocRefs = [];
      let search;

      if (docRefsToUse) {
        search = new JsSearch.Search('uuid');
        search.addIndex('name');
        search.addIndex('lineageNames');
        search.addDocuments(docRefsToUse);

        if (filterTermToUse && filterTermToUse.length > 1) {
          filteredDocRefs = search.search(filterTermToUse);
        } else if (!alwaysFilterToUse) {
          filteredDocRefs = docRefsToUse;
        }
      }

      // Filter on doc ref types if they have been received
      if (listingState.docRefTypesReceived) {
        filteredDocRefs = filteredDocRefs.filter(d => docRefTypeFiltersToUse.includes(d.type));
      }

      return {
        ...state,
        [listingId]: {
          ...listingState,
          docRefs: docRefsToUse,
          fixedDocRefTypeFilters: fixedDocRefTypeFiltersToUse,
          docRefTypeFilters: docRefTypeFiltersToUse,
          filterTerm: filterTermToUse,
          allowMultiSelect: allowMultiSelectToUse,
          alwaysFilter: alwaysFilterToUse,
          filteredDocRefs,
          search,
        },
      };
    },
    [combineActions(docRefSelectionUp, docRefSelectionDown)]: (state, action) => {
      const {
        payload: { listingId, selectionChange },
      } = action;
      const listingState = state[listingId];
      const { filteredDocRefs, selectedItem } = listingState;

      let nextIndex = 0;
      if (selectedItem !== -1) {
        nextIndex =
          (filteredDocRefs.length + (selectedItem + selectionChange)) % filteredDocRefs.length;
      }

      return {
        ...state,
        [listingId]: {
          ...listingState,
          selectedItem: nextIndex,
        },
      };
    },
    DOC_REF_CHECK_TOGGLED: (state, action) => {
      const {
        payload: { listingId, uuid, isChecked },
      } = action;
      const listingState = state[listingId];

      const isCurrentlySelected = listingState.checkedDocRefUuids.includes(uuid);
      let checkedDocRefUuids = listingState.checkedDocRefUuids;
      if (isCurrentlySelected) {
        checkedDocRefUuids = checkedDocRefUuids.filter(u => u !== uuid);
      } else if (listingState.allowMultiSelect) {
        checkedDocRefUuids = checkedDocRefUuids.concat([uuid]);
      } else {
        checkedDocRefUuids = [uuid];
      }

      return {
        ...state,
        [listingId]: {
          ...listingState,
          checkedDocRefUuids,
        },
      };
    },
  },
  defaultState,
);

export { actionCreators, reducer };
