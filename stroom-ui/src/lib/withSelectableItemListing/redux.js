import { createActions, handleActions, combineActions } from 'redux-actions';

import { createActionHandlerPerId } from 'lib/reduxFormUtils';

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

const defaultSelectableItemListingState = {
  items: [],
  focusIndex: -1, // Used for simple item selection, by array index
  lastSelectedIndex: -1,
  selectedItems: [],
  selectedItemIndexes: new Set(),
};

// There will be an entry for each listing ID registered
const defaultState = {};

const byListingId = createActionHandlerPerId(
  ({ payload: { listingId } }) => listingId,
  defaultSelectableItemListingState,
);

const reducer = handleActions(
  {
    SELECTABLE_LISTING_MOUNTED: byListingId((state, action, listingState) => {
      const {
        payload: { listingId, items, selectionBehaviour },
      } = action;

      // Attempt to rescue previous focus index
      let focusIndex = -1;
      let focussedItem;
      if (listingState) {
        if (listingState.focusIndex < items.length) {
          focusIndex = listingState.focusIndex;
          focussedItem = items[focusIndex];
        }
      }

      return {
        focusIndex,
        focussedItem,
        items,
        selectionBehaviour,
      };
    }),
    [combineActions(focusUp, focusDown)]: byListingId((state, action, listingState) => {
      const {
        payload: { listingId, direction },
      } = action;

      const { items, focusIndex } = listingState;

      // Calculate the next index based on the selection change
      let nextIndex = 0;
      if (focusIndex !== -1) {
        nextIndex = (items.length + (focusIndex + direction)) % items.length;
      }
      const focussedItem = items[nextIndex];

      return {
        focusIndex: nextIndex,
        focussedItem,
      };
    }),
    [combineActions(selectFocussed, selectionToggled)]: byListingId((state, action, listingState) => {
      const {
        payload: { listingId, index, keyIsDown },
      } = action;

      let {
        selectedItemIndexes,
        focusIndex,
        lastSelectedIndex,
        items,
        selectionBehaviour,
      } = listingState;
      const indexToUse = index !== undefined && index >= 0 ? index : focusIndex;

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
      const focussedItem = items.find((item, i) => i === indexToUse);

      return {
        focussedItem,
        selectedItems,
        selectedItemIndexes,
        focusIndex: indexToUse,
        lastSelectedIndex: indexToUse,
      };
    }),
  },
  defaultState,
);

export { actionCreators, reducer, SELECTION_BEHAVIOUR, defaultSelectableItemListingState };
