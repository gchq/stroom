import { createActions, handleActions } from 'redux-actions';

const actionCreators = createActions({
  START_INHERITED_PIPELINE: pipelineId => ({ pipelineId }),
});

const defaultState = {
  pipelineId: undefined,
};

const reducer = handleActions(
  {
    START_INHERITED_PIPELINE: (state, action) => ({
      ...state,
      pipelineId: action.payload.pipelineId,
    }),
  },
  defaultState,
);

export { actionCreators, reducer };
