import * as React from "react";
import useKeyIsDown, { KeyDownState } from "lib/useKeyIsDown";

interface InProps<T extends {}> {
  items: T[];
  getKey: (item: T) => string;
}

interface OutProps<T extends {}> {
  selectedItems: T[];
  selectedIndexes: number[];
  selectedKeys: string[];
  lastSelectedKey: string | undefined;
  lastSelectedIndex: number | undefined;
  toggleSelection: (key: string) => void;
  clearSelection: () => void;
}

interface SelectionToggled {
  type: "toggled";
  key: string;
  isCtrlDown: boolean;
  isShiftDown: boolean;
}

interface SelectionCleared {
  type: "cleared";
}

interface UpdateRawKeys {
  type: "updateRawKeys";
  rawKeys: string[];
}

type ActionType = SelectionToggled | SelectionCleared | UpdateRawKeys;

interface InnerReducerState {
  selectedKeys: string[];
  rawKeys: string[];
  lastSelectedKey: string | undefined;
}

/**
 * This inner reducer calculates the new values of selected based on keys.
 * The outer reducer then derives properties based on index within the raw list.
 */
const innerReducer = (
  state: InnerReducerState,
  action: ActionType,
): InnerReducerState => {
  const { selectedKeys, rawKeys, lastSelectedKey } = state;
  switch (action.type) {
    case "toggled": {
      const { key, isShiftDown, isCtrlDown } = action;
      // If CTRL is down (includes Meta) then simply add the keys
      if (isCtrlDown) {
        if (selectedKeys.includes(key)) {
          const newSelectedKeys: string[] = selectedKeys.filter(
            (k) => k !== key,
          );
          return {
            rawKeys,
            selectedKeys: newSelectedKeys,
            lastSelectedKey: key,
          };
        } else {
          const newSelectedKeys = [...selectedKeys, key];
          return {
            rawKeys,
            selectedKeys: newSelectedKeys,
            lastSelectedKey: key,
          };
        }
      } else if (isShiftDown) {
        // If Shift is held down, try to create a contiguous selection from the last selected item.
        let newSelectedKeys: string[] = [];
        const lastSelectedIndex = rawKeys.indexOf(lastSelectedKey);
        const thisSelectedIndex = rawKeys.indexOf(key);

        if (lastSelectedKey === undefined) {
          newSelectedKeys.push(key);
        } else if (thisSelectedIndex < lastSelectedIndex) {
          newSelectedKeys = rawKeys.slice(
            thisSelectedIndex,
            lastSelectedIndex + 1,
          );
        } else {
          newSelectedKeys = rawKeys.slice(
            lastSelectedIndex,
            thisSelectedIndex + 1,
          );
        }

        return {
          rawKeys,
          selectedKeys: newSelectedKeys,
          lastSelectedKey: key,
        };
      } else {
        const newSelectedKeys: string[] = selectedKeys.includes(key)
          ? []
          : [key];
        return {
          rawKeys,
          selectedKeys: newSelectedKeys,
          lastSelectedKey: key,
        };
      }
    }
    case "cleared":
      return {
        rawKeys,
        selectedKeys: [],
        lastSelectedKey: undefined,
      };
    case "updateRawKeys":
      return {
        rawKeys: action.rawKeys,
        selectedKeys: selectedKeys.filter((s) => action.rawKeys.includes(s)),
        lastSelectedKey: action.rawKeys.includes(lastSelectedKey)
          ? lastSelectedKey
          : undefined,
      };
    default:
      return state;
  }
};

interface ReducerState extends InnerReducerState {
  selectedIndexes: number[];
  lastSelectedIndex: number | undefined;
}

/**
 * Using a layered reducer so that I don't have to repeat the calculation of the
 * properties being added by the outer reducer
 */
const reducer = (state: ReducerState, action: ActionType): ReducerState => {
  const innerState = innerReducer(state, action);

  const { selectedKeys, rawKeys, lastSelectedKey } = innerState;

  return {
    ...innerState,
    selectedIndexes: rawKeys
      .map((k, i) => (selectedKeys.includes(k) ? i : undefined))
      .filter((i) => i !== undefined),
    lastSelectedIndex: rawKeys.findIndex((d) => d === lastSelectedKey),
  };
};

const keyDownFilters: string[] = ["Control", "Shift", "Meta"];

export const useSelectable = <T extends {}>({
  items,
  getKey,
}: InProps<T>): OutProps<T> => {
  const keyIsDown: KeyDownState = useKeyIsDown(keyDownFilters);
  const [
    { selectedKeys, lastSelectedKey, lastSelectedIndex, selectedIndexes },
    dispatch,
  ] = React.useReducer(reducer, {
    rawKeys: [],
    selectedKeys: [],
    selectedIndexes: [],
    lastSelectedKey: undefined,
    lastSelectedIndex: undefined,
  });
  React.useEffect(() => {
    const rawKeys: string[] = items.map(getKey);
    dispatch({ type: "updateRawKeys", rawKeys });
  }, [items, dispatch, getKey]);

  const toggleSelection = React.useCallback(
    (key: string) =>
      dispatch({
        type: "toggled",
        key,
        isCtrlDown: keyIsDown["Control"] || keyIsDown["Meta"],
        isShiftDown: keyIsDown["Shift"],
      }),
    [dispatch, keyIsDown],
  );
  const clearSelection = React.useCallback(
    () => dispatch({ type: "cleared" }),
    [dispatch],
  );

  const selectedItems: T[] = React.useMemo(
    () => items.filter((item) => selectedKeys.includes(getKey(item))),
    [selectedKeys, getKey, items],
  );

  return {
    selectedItems,
    selectedIndexes,
    selectedKeys,
    lastSelectedKey,
    lastSelectedIndex,
    toggleSelection,
    clearSelection,
  };
};
