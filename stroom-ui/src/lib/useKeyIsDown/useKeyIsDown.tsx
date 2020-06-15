import * as React from "react";
import { KeyDownState } from "./types";

export const DEFAULT_FILTERS = ["Control", "Shift", "Alt", "Meta"];

interface KeyAction {
  isDown: boolean;
  key: string;
}

const reducer = (
  state: KeyDownState,
  { key, isDown }: KeyAction,
): KeyDownState => {
  return {
    ...state,
    [key]: isDown,
  };
};

const useKeyIsDown = function (
  filters: string[] = DEFAULT_FILTERS,
): KeyDownState {
  const [keysDown, dispatch] = React.useReducer(
    reducer,
    filters.reduce((acc, c) => ({ ...acc, [c]: false }), {}),
  );

  React.useEffect(() => {
    const onkeydown = (e: KeyboardEvent) => {
      if (filters.includes(e.key)) {
        dispatch({ key: e.key, isDown: true });
      }
    };
    document.addEventListener("keydown", onkeydown);

    const onkeyup = (e: KeyboardEvent) => {
      if (filters.includes(e.key)) {
        dispatch({ key: e.key, isDown: false });
      }
    };
    document.addEventListener("keyup", onkeyup);

    return () => {
      document.removeEventListener("keydown", onkeydown);
      document.removeEventListener("keyup", onkeyup);
    };
  }, [filters, dispatch]); // empty array prevents this code re-running for state changes

  return keysDown;
};

export default useKeyIsDown;
