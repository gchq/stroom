import { createActions, combineActions, handleActions } from 'redux-actions';

const actionCreators = createActions({
  START_DOC_REF_CREATION: () => ({ isOpen: true }),
  CANCEL_DOC_REF_CREATION: () => ({ isOpen: false }),
});

const { startDocRefCreation, cancelDocRefCreation } = actionCreators;

const defaultState = {
  isOpen: false,
};

const reducer = handleActions(
  {
    [combineActions(startDocRefCreation, cancelDocRefCreation)]: (
      state,
      { payload: { isOpen } },
    ) => ({
      isOpen,
    }),
  },
  defaultState,
);

export { actionCreators, reducer };
