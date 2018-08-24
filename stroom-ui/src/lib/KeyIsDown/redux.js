import { createActions, handleActions, combineActions } from 'redux-actions';

const actionCreators = createActions({
  KEY_DOWN: keyCode => ({ keyCode, isDown: true }),
  KEY_UP: keyCode => ({ keyCode, isDown: false }),
});

const { keyDown, keyUp } = actionCreators;

const defaultState = {};

const reducer = handleActions(
  {
    [combineActions(keyDown, keyUp)]: (state, { payload: { keyCode, isDown } }) => ({
      ...state,
      [keyCode]: isDown,
    }),
  },
  defaultState,
);

export { actionCreators, reducer };
