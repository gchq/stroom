import * as React from "react";

/**
 * An updateable state, the value is available, and a function that can accept partial updates.
 * When the update function is called, the updates are merged in with the existing value.
 */
// eslint-disable-next-line @typescript-eslint/ban-types
interface UseUpdateableState<T> {
  value: T;
  update: (updates: Partial<T>) => void;
}

/**
 * This hook adds the ability to partially update a React.useState.
 *
 * @param initialValue A complete initial value for the state
 */
export const useUpdateableState = <T>(
  initialValue: T,
): UseUpdateableState<T> => {
  const reducer = (state: T, action: Partial<T>) => ({
    ...state,
    ...action,
  });
  const [value, update] = React.useReducer(reducer, initialValue);

  return {
    value,
    update,
  };
};

export default useUpdateableState;
