import { createActions, handleActions, combineActions } from 'redux-actions';

const actionCreators = createActions({
  APP_SEARCH_OPENED: () => ({ isOpen: true }),
  APP_SEARCH_CLOSED: () => ({ isOpen: false }),
});

const { appSearchOpened, appSearchClosed } = actionCreators;

const defaultState = {
  isOpen: false,
};

const reducer = handleActions(
  {
    [combineActions(appSearchOpened, appSearchClosed)]: (state, { payload: { isOpen } }) => ({
      ...state,
      isOpen,
    }),
  },
  defaultState,
);

export { actionCreators, reducer };
