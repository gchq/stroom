import { createActions, handleActions } from 'redux-actions';

const actionCreators = createActions({
  FOLDER_ENTRY_SELECTED: (uuid, index) => ({ uuid, index }),
});

const defaultState = {
  selected: {},
};

const reducer = handleActions(
  {
    FOLDER_ENTRY_SELECTED: (state, { payload: { uuid, index } }) => ({
      ...state,
      selected: {
        ...state.selected,
        [uuid]: index,
      },
    }),
  },
  defaultState,
);

export { actionCreators, reducer };
