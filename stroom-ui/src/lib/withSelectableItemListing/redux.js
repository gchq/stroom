import { createActions, handleActions, combineActions } from 'redux-actions';

const SELECTION_BEHAVIOUR = {
  NONE: 0,
  SINGLE: 1,
  MULTIPLE: 2,
};

const actionCreators = createActions({
  SELECTABLE_LISTING_MOUNTED: (listingId, items, selectionBehaviour) => ({
    listingId,
    items,
    selectionBehaviour,
  }),
  FOCUS_UP: listingId => ({ listingId, direction: -1 }),
  FOCUS_DOWN: listingId => ({ listingId, direction: 1 }),
  SELECT_FOCUSSED: (listingId, keyIsDown = {}) => ({ listingId, keyIsDown }),
  SELECTION_TOGGLED: (listingId, index, keyIsDown = {}) => ({ listingId, index, keyIsDown }),
});

const {
  focusUp, focusDown, selectFocussed, selectionToggled,
} = actionCreators;

const defaultStatePerListing = {
  items: [],
  focusIndex: -1, // Used for simple item selection, by array index
  lastSelectedIndex: -1,
  selectedItems: [],
  selectedItemIndexes: new Set(),
};

// There will be an entry for each listing ID registered
const defaultState = {};

const reducer = handleActions(
  {
    SELECTABLE_LISTING_MOUNTED: (state, action) => {
      const {
        payload: { listingId, items, selectionBehaviour },
      } = action;

      return {
        ...state,
        [listingId]: {
          ...defaultStatePerListing,
          ...state[listingId], // any existing state
          items,
          selectionBehaviour,
        },
      };
    },
    [combineActions(focusUp, focusDown)]: (state, action) => {
      const {
        payload: { listingId, direction },
      } = action;

      const listingState = state[listingId];
      const { items, focusIndex } = listingState;

      // Calculate the next index based on the selection change
      let nextIndex = 0;
      if (focusIndex !== -1) {
        nextIndex = (items.length + (focusIndex + direction)) % items.length;
      }
      const focussedItem = items.find((item, i) => i === nextIndex);

      return {
        ...state,
        [listingId]: {
          ...listingState,
          focusIndex: nextIndex,
          focussedItem,
        },
      };
    },
    [combineActions(selectFocussed, selectionToggled)]: (state, action) => {
      const {
        payload: { listingId, index, keyIsDown },
      } = action;
      const listingState = state[listingId];
      let {
        selectedItemIndexes,
        focusIndex,
        lastSelectedIndex,
        items,
        selectionBehaviour,
      } = listingState;
      const indexToUse = index !== undefined ? index : focusIndex;

      if (selectionBehaviour !== SELECTION_BEHAVIOUR.NONE) {
        const isCurrentlySelected = selectedItemIndexes.has(indexToUse);
        if (isCurrentlySelected) {
          if (keyIsDown.Control || keyIsDown.Meta) {
            selectedItemIndexes = selectedItemIndexes.delete(indexToUse);
          } else {
            selectedItemIndexes = new Set();
          }
        } else if (selectionBehaviour === SELECTION_BEHAVIOUR.MULTIPLE) {
          if (keyIsDown.Control || keyIsDown.Meta) {
            selectedItemIndexes.add(indexToUse);
          } else if (keyIsDown.Shift) {
            selectedItemIndexes = new Set();
            items.forEach((n, nIndex) => {
              if (focusIndex < lastSelectedIndex) {
                for (let i = focusIndex; i <= lastSelectedIndex; i++) {
                  selectedItemIndexes.add(i);
                }
              } else {
                for (let i = lastSelectedIndex; i <= focusIndex; i++) {
                  selectedItemIndexes.add(i);
                }
              }
            });
          } else {
            selectedItemIndexes = new Set([indexToUse]);
          }
        } else {
          selectedItemIndexes = new Set([indexToUse]);
        }
      }

      const selectedItems = [];
      selectedItemIndexes.forEach(i => selectedItems.push(items[i]));

      return {
        ...state,
        [listingId]: {
          ...listingState,
          selectedItems,
          selectedItemIndexes,
          focusIndex: indexToUse,
          lastSelectedIndex: indexToUse,
        },
      };
    },
  },
  defaultState,
);

export { actionCreators, reducer, SELECTION_BEHAVIOUR };
