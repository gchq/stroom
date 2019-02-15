import { useCallback, useEffect } from "react";
import { useDispatch, useMappedState } from "redux-react-hook";
import { GlobalStoreState } from "src/startup/reducers";
import { Dispatch } from "redux";

/**
 * This effectively converts a React Redux Thunk into a Hook.
 *
 * @param thunk The thunk that would normally be dispatched, it will be a function that
 * has been called with the domain specific arguments, to return a functio that accepts dispatch & getState.
 */
export const useThunk = (
  thunk: (dispatch: Dispatch, getState: () => GlobalStoreState) => any
): void => {
  const mapState = useCallback(
    (state: GlobalStoreState) => ({
      state
    }),
    []
  );

  // Get data from and subscribe to the store
  const { state } = useMappedState(mapState);
  const dispatch = useDispatch();

  useEffect(() => {
    thunk(dispatch, () => state);
  }, []);
};

export default useThunk;
