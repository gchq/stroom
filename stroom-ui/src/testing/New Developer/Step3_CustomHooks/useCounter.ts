import * as React from "react";

/**
 * This is an example of a custom hook.
 *
 * It returns a count value, and two functions to modify that count.
 *
 *
 */

interface UseCounter {
  count: number;
  increment: () => void;
  decrement: () => void;
}

type IncDec = "increment" | "decrement";

function countReducer(state: number, action: IncDec) {
  switch (action) {
    case "increment":
      return state + 1;
    case "decrement":
      return state - 1;
    default:
      return state;
  }
}

export const useCounter = (): UseCounter => {
  const [count, dispatch] = React.useReducer(countReducer, 0);

  /**
   *  Use callback means it will only regenerate these functions if the values of the arguments
   * change. In this case, they will change quite often, but in some other places, this use of memo-ization
   * saves a lot of performance. It is also essential to memo-ize things to prevent infinite recursive render loops.
   */
  const increment = React.useCallback(() => dispatch("increment"), [dispatch]);
  const decrement = React.useCallback(() => dispatch("decrement"), [dispatch]);

  return {
    count,
    increment,
    decrement,
  };
};
