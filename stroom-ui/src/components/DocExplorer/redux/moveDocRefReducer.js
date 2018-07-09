import { createActions, combineActions, handleActions } from 'redux-actions';

const actionCreators = createActions({
  PREPARE_DOC_REF_MOVES: docRefs => ({ docRefs }),
  COMPLETE_DOC_REF_MOVES: () => ({ docRefs: [] }),
});

const { prepareDocRefMoves, completeDocRefMoves } = actionCreators;

// Array of doc refs being moved
const defaultState = { isMoving: false, docRefs: [] };

const reducer = handleActions(
  {
    [combineActions(prepareDocRefMoves, completeDocRefMoves)]: (
      state,
      { payload: { docRefs } },
    ) => ({
      isMoving: docRefs.length > 0,
      docRefs,
    }),
  },
  defaultState,
);

export { actionCreators, reducer };
