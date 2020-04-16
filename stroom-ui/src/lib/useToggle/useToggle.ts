import * as React from "react";

interface UseToggle {
  value: boolean;
  toggle: () => void;
}

function reducer(state: boolean) {
  return !state;
}

const useToggle = (defaultValue: boolean = false): UseToggle => {
  const [value, dispatch] = React.useReducer(reducer, defaultValue);
  const toggle = React.useCallback(() => dispatch(), [dispatch]);

  return {
    value,
    toggle,
  };
};

export default useToggle;
