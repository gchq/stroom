import { createActions, handleActions } from 'redux-actions';

const defaultState = {};

const actionCreators = createActions({
  MENU_ITEM_OPENED: (key, isOpen) => ({ key, isOpen }),
});

const reducer = handleActions(
  {
    MENU_ITEM_OPENED: (state, { payload: { key, isOpen } }) => ({
      ...state,
      [key]: isOpen,
    }),
  },
  defaultState,
);

export { actionCreators, reducer };
