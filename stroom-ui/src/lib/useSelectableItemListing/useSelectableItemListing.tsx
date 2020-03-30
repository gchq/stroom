import * as React from "react";
import { InProps, OutProps } from "./types";
import { SelectionBehaviour } from "./enums";
import useOnKeyDown from "lib/useOnKeyDown";
import useCustomFocus from "lib/useCustomFocus";
import useSelectable from "lib/useSelectable/useSelectable";

const useSelectableItemListing = <TItem extends {}>({
  getKey,
  items,
  openItem,
  enterItem,
  goBack,
  selectionBehaviour = SelectionBehaviour.NONE,
  preFocusWrap,
}: InProps<TItem>): OutProps<TItem> => {
  const { up, down, setByIndex, focusIndex, highlightedItem } = useCustomFocus<
    TItem
  >({
    items,
    preFocusWrap,
  });
  const {
    toggleSelection,
    clearSelection,
    selectedItems,
    selectedIndexes,
    lastSelectedKey,
    lastSelectedIndex,
  } = useSelectable<TItem>({ items, getKey });

  const enterItemOnKey = React.useCallback(
    (e: React.KeyboardEvent) => {
      if (highlightedItem) {
        if (!!openItem) {
          openItem(highlightedItem);
        } else {
          toggleSelection(getKey(highlightedItem));
        }
      }
      e.preventDefault();
    },
    [highlightedItem, openItem, toggleSelection, getKey],
  );
  const goRight = React.useCallback(
    (e: React.KeyboardEvent) => {
      if (!!highlightedItem) {
        if (!!enterItem) {
          enterItem(highlightedItem);
        } else if (!!openItem) {
          openItem(highlightedItem);
        }
      }
      e.preventDefault();
    },
    [highlightedItem, enterItem, openItem],
  );
  const goLeft = React.useCallback(
    (e: React.KeyboardEvent) => {
      if (!!highlightedItem && !!goBack) {
        goBack(highlightedItem);
      }
      e.preventDefault();
    },
    [highlightedItem, goBack],
  );
  const spaceKey = React.useCallback(
    (e: React.KeyboardEvent) => {
      if (selectionBehaviour !== SelectionBehaviour.NONE) {
        toggleSelection(getKey(highlightedItem));
        e.preventDefault();
      }
    },
    [selectionBehaviour, toggleSelection, getKey, highlightedItem],
  );

  const toggleSelectionAndFocus = React.useCallback(
    (itemKey: string) => {
      toggleSelection(itemKey);
      setByIndex(items.findIndex(item => getKey(item) === itemKey));
    },
    [items, getKey, toggleSelection, setByIndex],
  );

  const onKeyDown = useOnKeyDown({
    ArrowUp: up,
    k: up,
    ArrowDown: down,
    j: down,
    ArrowLeft: goLeft,
    h: goLeft,
    ArrowRight: goRight,
    l: goRight,
    Enter: enterItemOnKey,
    " ": spaceKey,
  });

  const selectedItem: TItem | undefined = React.useMemo(
    () =>
      selectedItems.length > 0 && !!lastSelectedKey
        ? selectedItems.find(item => getKey(item) === lastSelectedKey)
        : undefined,
    [selectedItems, lastSelectedKey, getKey],
  );

  return {
    focusIndex,
    highlightedItem,
    selectedItems,
    selectedIndexes,
    selectedItem,
    lastSelectedIndex,
    toggleSelection: toggleSelectionAndFocus,
    clearSelection,
    onKeyDown,
  };
};

export default useSelectableItemListing;
