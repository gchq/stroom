import { createActions, combineActions, handleActions } from 'redux-actions';

import { actionCreators as explorerTreeActionCreators } from './explorerTreeReducer';

const { explorerTreeOpened } = explorerTreeActionCreators;

const actionCreators = createActions({
  PREPARE_DOC_REF_MOVES: (explorerId, docRefs) => ({ explorerId, docRefs }),
  COMPLETE_DOC_REF_MOVES: explorerId => ({ explorerId, docRefs: [] }),
});

const { prepareDocRefMoves, completeDocRefMoves } = actionCreators;

// The state will contain a map of arrays.
// Keyed on explorer ID, the arrays will contain the doc refs being moved
const defaultState = {};

const reducer = handleActions(
  {
    [combineActions(prepareDocRefMoves, completeDocRefMoves)]: (
      state,
      { payload: { explorerId, docRefs } },
    ) => ({
      ...state,
      [explorerId]: docRefs,
    }),
    [explorerTreeOpened]: (state, action) => ({
      ...state,
      [action.payload.explorerId]: [],
    }),
  },
  defaultState,
);

export { actionCreators, reducer };
