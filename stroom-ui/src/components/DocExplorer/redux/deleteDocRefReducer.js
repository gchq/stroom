import { createActions, combineActions, handleActions } from 'redux-actions';

const actionCreators = createActions({
  PREPARE_DOC_REF_DELETE: docRef => ({ docRef }),
  COMPLETE_DOC_REF_DELETE: () => ({ docRef: [] }),
});

const { prepareDocRefDelete, completeDocRefDelete } = actionCreators;

// The state will contain a map of arrays.
// Keyed on explorer ID, the arrays will contain the doc refs being moved
const defaultState = { isDeleting: false, docRefs: [] };

const reducer = handleActions(
  {
    [combineActions(prepareDocRefDelete, completeDocRefDelete)]: (
      state,
      { payload: { docRefs } },
    ) => ({
      isDeleting: docRefs.length > 0,
      docRefs,
    }),
  },
  defaultState,
);

export { actionCreators, reducer };
