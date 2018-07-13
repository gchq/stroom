import { createActions, handleActions } from 'redux-actions';

const actionCreators = createActions({
  PIPELINES_RECEIVED: (total, pipelines) => ({
    total,
    pipelines,
  }),
});

const defaultPipelineSearchState = {
  total: undefined,
  pipelines: [],
};

const reducer = handleActions(
  {
    PIPELINES_RECEIVED: (state, action) => ({
      ...state,
      total: action.payload.total,
      pipelines: action.payload.pipelines,
    }),
  },
  defaultPipelineSearchState,
);

export { actionCreators, reducer };
