import { createActions, combineActions, handleActions } from 'redux-actions';

import { actionCreators as documentTreeActionCreators } from './documentTree';

const { docRefCreated } = documentTreeActionCreators;

const actionCreators = createActions({
  PREPARE_DOC_REF_CREATION: destination => ({ isOpen: true, destination }),
  COMPLETE_DOC_REF_CREATION: () => ({ isOpen: false, destination: undefined }),
});

const { prepareDocRefCreation, completeDocRefCreation } = actionCreators;

const defaultState = {
  isOpen: false,
  destination: undefined,
};

const reducer = handleActions(
  {
    [combineActions(prepareDocRefCreation, completeDocRefCreation)]: (
      state,
      { payload: { isOpen, destination } },
    ) => ({
      isOpen,
      destination,
    }),
    [docRefCreated]: () => defaultState,
  },
  defaultState,
);

export { actionCreators, reducer };
