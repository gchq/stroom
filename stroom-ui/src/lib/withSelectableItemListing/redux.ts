import { Action } from "redux";

import { StoreState as KeyIsDownStoreState } from "../KeyIsDown/redux";
import { prepareReducerById, StateById, ActionId } from "../redux-actions-ts";

export enum SelectionBehaviour {
  NONE,
  SINGLE,
  MULTIPLE
}

export type GetKey = (a: any) => string;

export interface StoreStatePerId {
  items: Array<any>;
  focusIndex: number;
  focussedItem?: any;
  lastSelectedIndex?: number;
  selectedItems: Array<any>;
  selectedItemIndexes: Set<number>;
  selectionBehaviour: SelectionBehaviour;
  getKey: GetKey;
}

export type StoreState = StateById<StoreStatePerId>;

export interface SelectableListingMounted extends ActionId {
  items: Array<any>;
  selectionBehaviour: SelectionBehaviour;
  getKey: GetKey;
}

export interface FocusChange extends ActionId {
  direction: -1 | 1;
}

export interface SelectionChange extends ActionId {
  itemKey?: string;
  keyIsDown: KeyIsDownStoreState;
}

const SELECTABLE_LISTING_MOUNTED = "SELECTABLE_LISTING_MOUNTED";
const FOCUS_UP = "FOCUS_UP";
const FOCUS_DOWN = "FOCUS_DOWN";
const SELECT_FOCUSSED = "SELECT_FOCUSSED";
const SELECTION_TOGGLED = "SELECTION_TOGGLED";

export const actionCreators = {
  selectableListingMounted: (
    id: string,
    items: Array<any>,
    selectionBehaviour: SelectionBehaviour | undefined,
    getKey: (a: any) => string
  ): SelectableListingMounted & Action<"SELECTABLE_LISTING_MOUNTED"> => ({
    type: SELECTABLE_LISTING_MOUNTED,
    id,
    items,
    selectionBehaviour: selectionBehaviour || SelectionBehaviour.NONE,
    getKey
  }),
  focusUp: (id: string): FocusChange & Action<"FOCUS_UP"> => ({
    type: FOCUS_UP,
    id,
    direction: -1
  }),
  focusDown: (
    id: string,
    direction: number
  ): FocusChange & Action<"FOCUS_DOWN"> => ({
    type: FOCUS_DOWN,
    id,
    direction: 1
  }),
  selectFocussed: (
    id: string,
    keyIsDown: KeyIsDownStoreState
  ): SelectionChange & Action<"SELECT_FOCUSSED"> => ({
    type: SELECT_FOCUSSED,
    id,
    keyIsDown
  }),
  selectionToggled: (
    id: string,
    itemKey: string,
    keyIsDown: KeyIsDownStoreState
  ): SelectionChange & Action<"SELECTION_TOGGLED"> => ({
    type: SELECTION_TOGGLED,
    id,
    itemKey,
    keyIsDown
  })
};

export const defaultStatePerId: StoreStatePerId = {
  items: [],
  focusIndex: -1, // Used for simple item selection, by array index
  focussedItem: undefined,
  lastSelectedIndex: -1,
  selectedItems: [],
  selectedItemIndexes: new Set(),
  selectionBehaviour: SelectionBehaviour.SINGLE,
  getKey: a => "nokey"
};

export const reducer = prepareReducerById(defaultStatePerId)
  .handleAction<Action & SelectableListingMounted>(
    SELECTABLE_LISTING_MOUNTED,
    (state: StoreStatePerId, { items, selectionBehaviour, getKey }) => {
      // Attempt to rescue previous focus index
      let focusIndex = -1;
      let focussedItem;
      if (state) {
        if (state.focusIndex < items.length) {
          focusIndex = state.focusIndex;
          focussedItem = items[focusIndex];
        }
      }

      return {
        ...defaultStatePerId,
        focusIndex,
        focussedItem,
        items,
        selectionBehaviour,
        getKey
      };
    }
  )
  .handleActions<Action & FocusChange>(
    [FOCUS_UP, FOCUS_DOWN],
    ({ items, focusIndex, ...rest }: StoreStatePerId, { direction }) => {
      // Calculate the next index based on the selection change
      let nextIndex = 0;
      if (focusIndex !== -1) {
        nextIndex = (items.length + (focusIndex + direction)) % items.length;
      }
      const focussedItem = items[nextIndex];

      return {
        ...rest,
        items,
        focusIndex: nextIndex,
        focussedItem
      };
    }
  )
  .handleActions<Action & SelectionChange>(
    [SELECT_FOCUSSED, SELECTION_TOGGLED],
    (state, { itemKey, keyIsDown }) => {
      const {
        selectedItemIndexes,
        focusIndex,
        lastSelectedIndex,
        items,
        getKey,
        selectionBehaviour,
        ...rest
      } = state!;
      const index = items.map(getKey).findIndex(k => k === itemKey);
      const indexToUse = index !== undefined && index >= 0 ? index : focusIndex;
      let newSelectedItemIndexes = new Set();

      if (selectionBehaviour !== SelectionBehaviour.NONE) {
        const isCurrentlySelected = selectedItemIndexes.has(indexToUse);
        if (isCurrentlySelected) {
          if (keyIsDown.Control || keyIsDown.Meta) {
            selectedItemIndexes.forEach(i => {
              if (i !== indexToUse) {
                newSelectedItemIndexes.add(i);
              }
            });
          }
        } else if (selectionBehaviour === SelectionBehaviour.MULTIPLE) {
          if (keyIsDown.Control || keyIsDown.Meta) {
            selectedItemIndexes.forEach(i => newSelectedItemIndexes.add(i));
            newSelectedItemIndexes.add(indexToUse);
          } else if (keyIsDown.Shift) {
            newSelectedItemIndexes = new Set();
            items.forEach((n: any, nIndex: number) => {
              if (!lastSelectedIndex) {
                newSelectedItemIndexes.add(focusIndex);
              } else if (focusIndex < lastSelectedIndex) {
                for (let i = focusIndex; i <= lastSelectedIndex; i++) {
                  newSelectedItemIndexes.add(i);
                }
              } else {
                for (let i = lastSelectedIndex; i <= focusIndex; i++) {
                  newSelectedItemIndexes.add(i);
                }
              }
            });
          } else {
            newSelectedItemIndexes.add(indexToUse);
          }
        } else {
          newSelectedItemIndexes.add(indexToUse);
        }
      }

      const selectedItems: Array<any> = [];
      newSelectedItemIndexes.forEach((i: number) =>
        selectedItems.push(items[i])
      );
      const focussedItem = items.find(
        (item: any, i: number) => i === indexToUse
      );

      return {
        ...rest,
        items,
        focussedItem,
        selectedItems,
        selectedItemIndexes: newSelectedItemIndexes,
        focusIndex: indexToUse,
        lastSelectedIndex: indexToUse,
        selectionBehaviour,
        getKey
      };
    }
  )
  .getReducer();
