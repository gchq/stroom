import { createActions, combineActions, handleActions } from 'redux-actions';
import * as JsSearch from 'js-search';

import { mapObject } from 'lib/treeUtils';
import { actionCreators as docRefTypeActionCreators } from 'components/DocRefTypes';

const { docRefTypesReceived } = docRefTypeActionCreators;

const actionCreators = createActions({
  DOC_REF_LISTING_MOUNTED: (listingId, docRefs, alwaysFilter) => ({
    listingId,
    docRefs,
    alwaysFilter,
  }),
  DOC_REF_LISTING_UNMOUNTED: listingId => ({
    listingId,
    selectedItem: 0,
    selectedDocRef: undefined,
  }),
  FILTER_TERM_UPDATED: (listingId, filterTerm) => ({ listingId, filterTerm }),
  DOC_REF_TYPE_FILTER_UPDATED: (listingId, docRefTypeFilters) => ({ listingId, docRefTypeFilters }),
  DOC_REF_SELECTION_UP: listingId => ({ listingId, selectionChange: -1 }),
  DOC_REF_SELECTION_DOWN: listingId => ({ listingId, selectionChange: +1 }),
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
  selectedItem: 0, // Used for simple item selection, by array index
  selectedDocRef: undefined, // Used for loading
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
          listingId, docRefs, alwaysFilter, filterTerm, docRefTypeFilters,
        },
      } = action;
      const listingState = state[listingId] || defaultStatePerListing;

      const alwaysFilterToUse =
        alwaysFilter !== undefined ? alwaysFilter : listingState.alwaysFilter;
      const filterTermToUse = filterTerm !== undefined ? filterTerm : listingState.filterTerm;
      const docRefsToUse = docRefs !== undefined ? docRefs : listingState.docRefs;
      const docRefTypeFiltersToUse =
        docRefTypeFilters !== undefined ? docRefTypeFilters : listingState.docRefTypeFilters;

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
          docRefTypeFilters: docRefTypeFiltersToUse,
          filterTerm: filterTermToUse,
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
      const { filteredDocRefs, selectedItem, selectedDocRef } = listingState;

      let nextIndex = 0;
      if (selectedDocRef) {
        nextIndex =
          (filteredDocRefs.length + (selectedItem + selectionChange)) % filteredDocRefs.length;
      }

      return {
        ...state,
        [listingId]: {
          ...listingState,
          selectedItem: nextIndex,
          selectedDocRef: filteredDocRefs[nextIndex],
        },
      };
    },
  },
  defaultState,
);

export { actionCreators, reducer };
