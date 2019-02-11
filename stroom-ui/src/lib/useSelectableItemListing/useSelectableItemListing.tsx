import { useState } from "react";
import useKeyIsDown from "../useKeyIsDown";
import { KeyDownState } from "../useKeyIsDown/useKeyIsDown";

export enum SelectionBehaviour {
  NONE,
  SINGLE,
  MULTIPLE
}

export interface InProps<TItem> {
  getKey: (x: TItem) => string;
  items: Array<TItem>;
  openItem: (i: TItem) => void;
  enterItem?: (i: TItem) => void;
  goBack?: (i: TItem) => void;
  selectionBehaviour?: SelectionBehaviour;
}

export interface OutProps {
  focusIndex: number;
  focussedItem?: any;
  lastSelectedIndex?: number;
  selectedItems: Array<any>;
  selectedItemIndexes: Set<number>;
  selectionToggled: (itemKey: string) => void;
  onKeyDownWithShortcuts: React.KeyboardEventHandler<HTMLDivElement>;
  keyIsDown: KeyDownState;
}

function useSelectableItemListing<TItem>({
  getKey,
  items,
  openItem,
  enterItem,
  goBack,
  selectionBehaviour = SelectionBehaviour.NONE
}: InProps<TItem>): OutProps {
  const keyIsDown = useKeyIsDown();
  const [focusIndex, setFocusIndex] = useState<number>(-1);
  const [focussedItem, setFocussedItem] = useState<TItem | undefined>(
    undefined
  );
  const [lastSelectedIndex, setLastSelectedIndex] = useState<number>(-1);
  const [selectedItems, setSelectedItems] = useState<Array<TItem>>([]);
  const [selectedItemIndexes, setSelectedItemIndexes] = useState<Set<number>>(
    new Set()
  );

  const focusChanged = (direction: number) => () => {
    let nextIndex = 0;
    if (focusIndex !== -1) {
      nextIndex = (items.length + (focusIndex + direction)) % items.length;
    }

    setFocusIndex(nextIndex);
    setFocussedItem(items[nextIndex]);
  };
  const focusUp = focusChanged(-1);
  const focusDown = focusChanged(+1);
  const selectionToggled = (itemKey?: string) => {
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

          if (lastSelectedIndex < 0) {
            newSelectedItemIndexes.add(indexToUse);
          } else if (indexToUse < lastSelectedIndex) {
            for (let i = indexToUse; i <= lastSelectedIndex; i++) {
              newSelectedItemIndexes.add(i);
            }
          } else {
            for (let i = lastSelectedIndex; i <= indexToUse; i++) {
              newSelectedItemIndexes.add(i);
            }
          }
        } else {
          newSelectedItemIndexes.add(indexToUse);
        }
      } else {
        newSelectedItemIndexes.add(indexToUse);
      }
    }

    const newSelectedItems: Array<any> = [];
    newSelectedItemIndexes.forEach((i: number) =>
      newSelectedItems.push(items[i])
    );
    const newFocussedItem = items[indexToUse];

    setFocussedItem(newFocussedItem);
    setSelectedItems(newSelectedItems);
    setSelectedItemIndexes(newSelectedItemIndexes);
    setFocusIndex(indexToUse);
    setLastSelectedIndex(indexToUse);
  };

  return {
    focusIndex,
    focussedItem,
    lastSelectedIndex,
    selectedItems,
    selectedItemIndexes,
    selectionToggled,
    keyIsDown,
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
          selectionToggled();
          e.preventDefault();
        }
      }
    }
  };
}

export default useSelectableItemListing;
