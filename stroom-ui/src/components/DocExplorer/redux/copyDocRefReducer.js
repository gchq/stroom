import { createActions, combineActions, handleActions } from 'redux-actions';

const actionCreators = createActions({
  PREPARE_DOC_REF_COPY: docRefs => ({ docRefs }),
  COMPLETE_DOC_REF_COPY: () => ({ docRefs: [] }),
});

const { prepareDocRefCopy, completeDocRefCopy } = actionCreators;

// The state will contain a map of arrays.
// Keyed on explorer ID, the arrays will contain the doc refs being moved
const defaultState = { isCopying: false, docRefs: [] };

const reducer = handleActions(
  {
    [combineActions(prepareDocRefCopy, completeDocRefCopy)]: (state, { payload: { docRefs } }) => ({
      isCopying: docRefs.length > 0,
      docRefs,
    }),
  },
  defaultState,
);

export { actionCreators, reducer };
