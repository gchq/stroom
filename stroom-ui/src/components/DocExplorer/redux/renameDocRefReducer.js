import { createActions, combineActions, handleActions } from 'redux-actions';

const actionCreators = createActions({
  PREPARE_DOC_REF_RENAME: docRef => ({ docRef }),
  COMPLETE_DOC_REF_RENAME: () => ({ docRef: undefined }),
});

const { prepareDocRefRename, completeDocRefRename } = actionCreators;

// The state will contain a map of arrays.
// Keyed on explorer ID, the arrays will contain the doc refs being moved
const defaultState = { isRenaming: false, docRef: undefined };

const reducer = handleActions(
  {
    [combineActions(prepareDocRefRename, completeDocRefRename)]: (
      state,
      { payload: { docRef } },
    ) => ({
      isRenaming: !!docRef,
      docRef,
    }),
  },
  defaultState,
);

export { actionCreators, reducer };
