import * as React from "react";

interface InProps<T extends {}> {
  items: T[];
  preFocusWrap?: () => boolean;
}

interface OutProps<T extends {}> {
  focusIndex: number;
  highlightedItem: T | undefined;
  setByIndex: (index: number) => void;
  setByItem: (item: T) => void;
  up: () => void;
  down: () => void;
  clear: () => void;
}

interface ReducerState {
  itemsLength: number;
  focusIndex: number;
}

const NO_FOCUS = -1;

interface SetFocus {
  type: "set";
  index: number;
}
interface ChangeFocus {
  type: "up" | "down" | "clear";
  preFocusWrap?: () => boolean;
}
interface SetItemsLength {
  type: "setLength";
  itemsLength: number;
}

const reducer = (
  state: ReducerState,
  action: SetFocus | ChangeFocus | SetItemsLength,
): ReducerState => {
  const { focusIndex, itemsLength } = state;
  switch (action.type) {
    case "set":
      return {
        ...state,
        focusIndex: action.index,
      };
    case "up": {
      const newFocusIndex = (focusIndex + -1 + itemsLength) % itemsLength;
      return {
        ...state,
        focusIndex: newFocusIndex,
      };
    }
    case "down": {
      let newFocusIndex = (focusIndex + 1) % itemsLength;
      if (
        !!action.preFocusWrap &&
        newFocusIndex < focusIndex &&
        !action.preFocusWrap()
      ) {
        newFocusIndex = focusIndex;
      }
      return { ...state, focusIndex: newFocusIndex };
    }
    case "clear":
      return {
        ...state,
        focusIndex: NO_FOCUS,
      };
    case "setLength":
      return {
        ...state,
        itemsLength: action.itemsLength,
        focusIndex: focusIndex % action.itemsLength,
      };
    default:
      return state;
  }
};

export const useCustomFocus = <T extends {}>({
  items,
  preFocusWrap,
}: InProps<T>): OutProps<T> => {
  const [{ focusIndex }, dispatch] = React.useReducer(reducer, {
    focusIndex: NO_FOCUS,
    itemsLength: items.length,
  });

  React.useEffect(
    () => dispatch({ type: "setLength", itemsLength: items.length }),
    [items, dispatch],
  );

  const highlightedItem: T | undefined = React.useMemo(() => {
    if (focusIndex > 0 && focusIndex < items.length) {
      return items[focusIndex];
    } else {
      return undefined;
    }
  }, [focusIndex, items]);

  const setByIndex = React.useCallback(
    (index: number) => dispatch({ type: "set", index }),
    [dispatch],
  );
  const setByItem = React.useCallback(
    (item: T) => dispatch({ type: "set", index: items.indexOf(item) }),
    [dispatch, items],
  );
  const up = React.useCallback(() => dispatch({ type: "up", preFocusWrap }), [
    dispatch,
    preFocusWrap,
  ]);
  const down = React.useCallback(
    () => dispatch({ type: "down", preFocusWrap }),
    [dispatch, preFocusWrap],
  );
  const clear = React.useCallback(() => dispatch({ type: "clear" }), [
    dispatch,
  ]);

  return {
    focusIndex,
    highlightedItem,
    setByIndex,
    setByItem,
    up,
    down,
    clear,
  };
};
