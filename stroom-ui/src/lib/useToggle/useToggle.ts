import * as React from "react";

interface UseToggle {
  value: boolean;
  toggle: () => void;
}

function reducer(state: boolean) {
  return !state;
}

export const useToggle = (defaultValue = false): UseToggle => {
  const [value, dispatch] = React.useReducer(reducer, defaultValue);
  const toggle = React.useCallback(() => dispatch(), [dispatch]);

  return {
    value,
    toggle,
  };
};
