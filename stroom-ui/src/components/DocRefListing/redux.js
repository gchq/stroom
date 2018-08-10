import { createActions, combineActions, handleActions } from 'redux-actions';
import * as JsSearch from 'js-search';

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
  DOC_REF_SELECTION_UP: listingId => ({ listingId, selectionChange: -1 }),
  DOC_REF_SELECTION_DOWN: listingId => ({ listingId, selectionChange: +1 }),
});

const {
  docRefListingMounted,
  docRefListingUnmounted,
  filterTermUpdated,
  docRefSelectionUp,
  docRefSelectionDown,
} = actionCreators;

const defaultStatePerListing = {
  docRefs: [],
  filteredDocRefs: [],
  filterTerm: '',
  selectedItem: 0, // Used for simple item selection, by array index
  selectedDocRef: undefined, // Used for loading
  search: undefined,
};

const defaultState = {};

const reducer = handleActions(
  {
    [combineActions(docRefListingMounted, docRefListingUnmounted, filterTermUpdated)]: (
      state,
      action,
    ) => {
      const {
        payload: {
          listingId, docRefs, alwaysFilter, filterTerm = '',
        },
      } = action;
      const listingState = state[listingId] || defaultStatePerListing;

      const alwaysFilterToUse =
        alwaysFilter !== undefined ? alwaysFilter : listingState.alwaysFilter;
      const filterTermToUse = filterTerm !== undefined ? filterTerm : listingState.filterTerm;
      const docRefsToUse = docRefs !== undefined ? docRefs : listingState.docRefs;

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

      return {
        ...state,
        [listingId]: {
          ...defaultStatePerListing,
          docRefs: docRefsToUse,
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
      const listingState = state[listingId] || defaultStatePerListing;
      const { filteredDocRefs, selectedItem } = listingState;

      const nextIndex =
        (filteredDocRefs.length + (selectedItem + selectionChange)) % filteredDocRefs.length;

      return {
        ...state,
        [listingId]: {
          ...state[listingId],
          selectedItem: nextIndex,
          selectedDocRef: filteredDocRefs[nextIndex],
        },
      };
    },
  },
  defaultState,
);

export { actionCreators, reducer };
