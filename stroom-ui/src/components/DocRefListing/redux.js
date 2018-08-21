import { createActions, combineActions, handleActions } from 'redux-actions';

const defaultStatePerListing = {
  allDocRefs: [],
  selectedItem: -1, // Used for simple item selection, by array index
  selectedDocRefUuids: [],
};

const defaultState = {};

const actionCreators = createActions({
  DOC_REF_LISTING_MOUNTED: (listingId, allDocRefs, allowMultiSelect) => ({
    listingId,
    allDocRefs,
    allowMultiSelect,
  }),
  DOC_REF_LISTING_UNMOUNTED: listingId => ({
    listingId,
    allDocRefs: [],
  }),
  DOC_REF_SELECTION_UP: listingId => ({ listingId, selectionChange: -1 }),
  DOC_REF_SELECTION_DOWN: listingId => ({ listingId, selectionChange: +1 }),
  DOC_REF_SELECTION_TOGGLED: (listingId, uuid, keyIsDown = {}) => ({ listingId, uuid, keyIsDown }),
});

const {
  docRefListingMounted,
  docRefListingUnmounted,
  docRefSelectionUp,
  docRefSelectionDown,
} = actionCreators;

const reducer = handleActions(
  {
    [combineActions(docRefListingMounted, docRefListingUnmounted)]: (state, action) => {
      const {
        payload: { listingId, allDocRefs, allowMultiSelect },
      } = action;

      return {
        ...state,
        [listingId]: {
          ...defaultStatePerListing,
          allDocRefs,
          allowMultiSelect,
        },
      };
    },
    [combineActions(docRefSelectionUp, docRefSelectionDown)]: (state, action) => {
      const {
        payload: { listingId, selectionChange },
      } = action;
      const listingState = state[listingId];
      const { allDocRefs, selectedItem } = listingState;

      let nextIndex = 0;
      if (selectedItem !== -1) {
        nextIndex = (allDocRefs.length + (selectedItem + selectionChange)) % allDocRefs.length;
      }

      const selectedDocRefUuids = [allDocRefs[nextIndex].uuid];

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
          listingId, uuid, keyIsDown,
        },
      } = action;
      const listingState = state[listingId];
      let {
        selectedDocRefUuids, selectedItem, allDocRefs, allowMultiSelect,
      } = listingState;

      const addToSelection = (arr, uuid) => {
        if (!arr.includes(uuid)) {
          arr.push(uuid);
        }
      };

      const isCurrentlySelected = selectedDocRefUuids.includes(uuid);
      if (isCurrentlySelected) {
        if (keyIsDown.Control || keyIsDown.Meta) {
          selectedDocRefUuids = selectedDocRefUuids.filter(u => u !== uuid);
        } else {
          selectedDocRefUuids = [];
        }
      } else if (allowMultiSelect) {
        if (keyIsDown.Control || keyIsDown.Meta) {
          selectedDocRefUuids = selectedDocRefUuids.concat([uuid]);
        } else if (keyIsDown.Shift) {
          let phase = 0;
          allDocRefs.forEach((n) => {
            switch (phase) {
              case 0: {
                // Looking for start of selection
                if (n.uuid === uuid) {
                  phase = 1;
                } else if (selectedDocRefUuids.includes(n.uuid)) {
                  phase = 2;
                }
                if (phase > 0) {
                  addToSelection(selectedDocRefUuids, n.uuid);
                }
                break;
              }
              case 1: {
                // Looking for existing selection
                addToSelection(selectedDocRefUuids, n.uuid);

                if (selectedDocRefUuids.includes(n.uuid)) {
                  phase = 100; // finished finding selection run
                }
                break;
              }
              case 2: {
                // Looking for the newly made selection
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

      allDocRefs.forEach((f, i) => {
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
