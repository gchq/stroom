import { createActions, handleActions, combineActions } from 'redux-actions';

const actionCreators = createActions({
  KEY_DOWN: keyCode => ({ keyCode, isDown: true }),
  KEY_UP: keyCode => ({ keyCode, isDown: false }),
  ELEMENT_FOCUSSED: (type, id) => ({ type, id }),
  ELEMENT_BLURRED: (type, id) => ({ type, id }),
});

const { keyDown, keyUp } = actionCreators;

const defaultState = {
  keyIsDown: {},
  focussedElement: undefined,
};

const reducer = handleActions(
  {
    [combineActions(keyDown, keyUp)]: (state, { payload: { keyCode, isDown } }) => ({
      ...state,
      keyIsDown: {
        ...state.keyIsDown,
        [keyCode]: isDown,
      },
    }),
    ELEMENT_FOCUSSED: (state, action) => ({
      ...state,
      focussedElement: {
        type: action.payload.type,
        id: action.payload.id,
      },
    }),
    ELEMENT_BLURRED: (state, action) => ({
      ...state,
      focussedElement: undefined,
    }),
  },
  defaultState,
);

export { actionCreators, reducer };
