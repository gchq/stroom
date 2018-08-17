import { createActions, combineActions, handleActions } from 'redux-actions';
import * as JsSearch from 'js-search';

import { mapObject } from 'lib/treeUtils';

const actionCreators = createActions({
  DOC_REF_LISTING_MOUNTED: (
    listingId,
    allDocRefs,
    maxResults,
    allowMultiSelect,
  ) => ({
    listingId,
    allDocRefs,
    maxResults,
    allowMultiSelect,
  }),
  DOC_REF_LISTING_UNMOUNTED: listingId => ({
    listingId,
    selectedItem: 0,
  }),
  FILTER_TERM_UPDATED: (listingId, filterTerm) => ({ listingId, filterTerm }),
  DOC_REF_SELECTION_UP: listingId => ({ listingId, selectionChange: -1 }),
  DOC_REF_SELECTION_DOWN: listingId => ({ listingId, selectionChange: +1 }),
  DOC_REF_SELECTION_TOGGLED: (listingId, uuid, keyIsDown = {}) => ({ listingId, uuid, keyIsDown }),
});

const {
  docRefListingMounted,
  docRefListingUnmounted,
  filterTermUpdated,
  docRefSelectionUp,
  docRefSelectionDown,
} = actionCreators;

const defaultStatePerListing = {
  allDocRefs: [],
  filteredDocRefs: [],
  docRefTypesReceived: false,
  filterTerm: '',
  selectedItem: -1, // Used for simple item selection, by array index
  selectedDocRefUuids: [],
  inMultiSelectMode: false,
  search: undefined,
};

const defaultState = {};

const reducer = handleActions(
  {
    [combineActions(
      docRefListingMounted,
      docRefListingUnmounted,
      filterTermUpdated,
    )]: (state, action) => {
      const {
        payload: {
          listingId,
          allDocRefs,
          maxResults,
          filterTerm,
          allowMultiSelect,
        },
      } = action;
      const listingState = state[listingId] || defaultStatePerListing;

      const allowMultiSelectToUse =
        allowMultiSelect !== undefined ? allowMultiSelect : listingState.allowMultiSelect;
      const maxResultsToUse = maxResults !== undefined ? maxResults : listingState.maxResults;
      const filterTermToUse = filterTerm !== undefined ? filterTerm : listingState.filterTerm;
      const docRefsToUse = allDocRefs !== undefined ? allDocRefs : listingState.allDocRefs;

      let filteredDocRefs = [];
      let search;

      if (docRefsToUse) {
        search = new JsSearch.Search('uuid');
        search.addIndex('name');
        search.addIndex('lineageNames');
        search.addDocuments(docRefsToUse);

        if (filterTermToUse && filterTermToUse.length > 1) {
          filteredDocRefs = search.search(filterTermToUse);
        } else if (maxResultsToUse === 0) {
          filteredDocRefs = docRefsToUse;
        } else {
          filteredDocRefs = docRefsToUse.filter((d, i) => i < maxResultsToUse);
        }
      }

      return {
        ...state,
        [listingId]: {
          ...listingState,
          allDocRefs: docRefsToUse,
          filterTerm: filterTermToUse,
          allowMultiSelect: allowMultiSelectToUse,
          maxResults: maxResultsToUse,
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

      const selectedDocRefUuids = [filteredDocRefs[nextIndex].uuid];

      return {
        ...state,
        [listingId]: {
          ...listingState,
          selectedItem: nextIndex,
          selectedDocRefUuids,
        },
      };
    },
    DOC_REF_SELECTION_TOGGLED: (state, action) => {
      const {
        payload: {
          listingId, uuid, isChecked, keyIsDown,
        },
      } = action;
      const listingState = state[listingId];
      let {
        selectedDocRefUuids, selectedItem, filteredDocRefs, allowMultiSelect,
      } = listingState;

      const addToSelection = (arr, uuid) => {
        if (!arr.includes(uuid)) {
          arr.push(uuid);
        }
      }

      const isCurrentlySelected = selectedDocRefUuids.includes(uuid);
      if (isCurrentlySelected) {
        if (keyIsDown.Control || keyIsDown.Meta) {
          selectedDocRefUuids = selectedDocRefUuids.filter(u => u !== uuid);
        } else {
          selectedDocRefUuids = []
        }
      } else if (allowMultiSelect) {
        if (keyIsDown.Control || keyIsDown.Meta) {
          selectedDocRefUuids = selectedDocRefUuids.concat([uuid]);
        } else if (keyIsDown.Shift) {
          let phase = 0;
          filteredDocRefs.forEach((n) => {
            const atAnEndpoint = n.uuid === uuid || selectedDocRefUuids.includes(n.uuid);

            switch (phase) {
              case 0: { // Looking for start of selection
                if (n.uuid === uuid) {
                  phase = 1
                } else if (selectedDocRefUuids.includes(n.uuid)) {
                  phase = 2
                }
                if (phase > 0) {
                  addToSelection(selectedDocRefUuids, n.uuid);
                }
                break;
              }
              case 1: { // Looking for existing selection
                addToSelection(selectedDocRefUuids, n.uuid);

                if (selectedDocRefUuids.includes(n.uuid)) {
                  phase = 100; // finished finding selection run
                }
              }
              case 2: { // Looking for the newly made selection
                addToSelection(selectedDocRefUuids, n.uuid);
                
                if (n.uuid === uuid) {
                  phase = 100; // finished finding selection run
                }
                break;
              }
              default:
                // done, just running to the end now
                break;
            }
          });
        } else {
          selectedDocRefUuids = [uuid];
        }
      } else {
        selectedDocRefUuids = [uuid];
      }

      filteredDocRefs.forEach((f, i) => {
        if (selectedDocRefUuids.includes(f.uuid)) {
          selectedItem = i;
        }
      });

      return {
        ...state,
        [listingId]: {
          ...listingState,
          selectedDocRefUuids,
          selectedItem,
        },
      };
    },
  },
  defaultState,
);

export { actionCreators, reducer };
