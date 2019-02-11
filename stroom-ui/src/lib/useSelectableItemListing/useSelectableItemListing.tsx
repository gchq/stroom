import { useState } from "react";

export enum SelectionBehaviour {
  NONE,
  SINGLE,
  MULTIPLE
}

const isArraysEqual = (a: Array<any>, b: Array<any>) => {
  if (a && !b) return false;
  if (!a && b) return false;
  if (!a && !b) return true;

  if (a.length !== b.length) return false;

  return a.filter(aItem => b.indexOf(aItem) === -1).length === 0;
};

export interface InProps<TItem> {
  getKey: (x: TItem) => string;
  items: Array<TItem>;
  openItem: (i: TItem) => void;
  enterItem?: (i: TItem) => void;
  goBack?: (i: TItem) => void;
  selectionBehaviour?: SelectionBehaviour;
}

export interface OutProps {
  onKeyDownWithShortcuts: React.KeyboardEventHandler<HTMLDivElement>;
}

let bob = false;

function useSelectableItemListing<TItem>({
  getKey,
  items,
  openItem,
  enterItem,
  goBack,
  selectionBehaviour
}: InProps<TItem>): OutProps {
  const [focusIndex, setFocusIndex] = useState<number>(-1);
  const [focussedItem, setFocussedItem] = useState<TItem | undefined>(
    undefined
  );
  const [lastSelectedIndex, setLastSelectedIndex] = useState<number>(-1);
  const [selectedItems, setSelectedItems] = useState<Array<TItem>>([]);
  const [selectedItemIndexes, setSelectedItemIndexes] = useState<Set<number>>(
    new Set()
  );

  const focusChanged = (direction: number) => () => {};
  const focusUp = focusChanged(-1);
  const focusDown = focusChanged(+1);
  const selectFocussed = () => {};

  if (!bob) {
    console.log({
      getKey,
      items,
      focusIndex,
      setFocusIndex,
      setFocussedItem,
      lastSelectedIndex,
      setLastSelectedIndex,
      selectedItemIndexes,
      selectedItems,
      setSelectedItems,
      setSelectedItemIndexes,
      isArraysEqual
    });
    bob = true;
  }

  return {
    onKeyDownWithShortcuts: (e: React.KeyboardEvent) => {
      if (e.key === "ArrowUp" || e.key === "k") {
        focusUp();
        e.preventDefault();
      } else if (e.key === "ArrowDown" || e.key === "j") {
        focusDown();
        e.preventDefault();
      } else if (e.key === "Enter") {
        if (focussedItem) {
          openItem(focussedItem);
        }
        e.preventDefault();
      } else if (e.key === "ArrowRight" || e.key === "l") {
        if (!!focussedItem) {
          if (!!enterItem) {
            enterItem(focussedItem);
          } else {
            openItem(focussedItem);
          }
        }
      } else if (e.key === "ArrowLeft" || e.key === "h") {
        if (!!focussedItem && !!goBack) {
          goBack(focussedItem);
        }
      } else if (e.key === " ") {
        if (selectionBehaviour !== SelectionBehaviour.NONE) {
          selectFocussed();
          e.preventDefault();
        }
      }
    }
  };
}

export default useSelectableItemListing;
