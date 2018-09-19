import { createActions, handleActions } from "redux-actions";
import { Action } from "redux";

export interface StoreState {
  [s: string]: boolean;
}

const defaultState = {};

export interface StoreAction {
  keyCode: string;
  isDown: boolean;
}

const baseActionCreator = createActions<StoreAction>({
  KEY_CHANGE: (keyCode, isDown) => ({ keyCode, isDown })
});

const actionCreators = {
  keyDown: (keyCode: string): Action =>
    baseActionCreator.keyChange(keyCode, true),
  keyUp: (keyCode: string): Action =>
    baseActionCreator.keyChange(keyCode, false)
};

const reducer = handleActions<StoreState, StoreAction>(
  {
    KEY_CHANGE: (state, { payload }) => ({
      ...state,
      [payload!.keyCode]: payload!.isDown
    })
  },
  defaultState
);

export { actionCreators, reducer };
