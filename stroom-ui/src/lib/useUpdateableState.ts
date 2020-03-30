import * as React from "react";

/**
 * An updateable state, the value is available, and a function that can accept partial updates.
 * When the update function is called, the updates are merged in with the existing value.
 */
interface UseUpdateableState<T extends object> {
  value: T;
  update: (updates: Partial<T>) => void;
}

const reducer = <T extends object>(state: T, action: Partial<T>) => ({
  ...state,
  ...action,
});

/**
 * This hook adds the ability to partially update a React.useState.
 *
 * @param initialValue A complete initial value for the state
 */
export const useUpdateableState = <T extends object>(
  initialValue: T,
): UseUpdateableState<T> => {
  const [value, update] = React.useReducer(reducer, initialValue);

  return {
    value,
    update,
  };
};

export default useUpdateableState;
