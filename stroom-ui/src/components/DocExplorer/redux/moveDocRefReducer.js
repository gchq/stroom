import { createActions, combineActions, handleActions } from 'redux-actions';

const actionCreators = createActions({
  PREPARE_DOC_REF_MOVE: docRefs => ({ docRefs }),
  COMPLETE_DOC_REF_MOVE: () => ({ docRefs: [] }),
});

const { prepareDocRefMove, completeDocRefMove } = actionCreators;

// Array of doc refs being moved
const defaultState = { isMoving: false, docRefs: [] };

const reducer = handleActions(
  {
    [combineActions(prepareDocRefMove, completeDocRefMove)]: (state, { payload: { docRefs } }) => ({
      isMoving: docRefs.length > 0,
      docRefs,
    }),
  },
  defaultState,
);

export { actionCreators, reducer };
