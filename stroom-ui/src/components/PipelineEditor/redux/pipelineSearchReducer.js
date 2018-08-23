import { createActions, handleActions } from 'redux-actions';

const actionCreators = createActions({
  PIPELINES_RECEIVED: (total, pipelines) => ({
    total,
    pipelines,
  }),
  UPDATE_CRITERIA: criteria => ({
    criteria,
  }),
});

const defaultPipelineSearchState = {
  total: undefined,
  pipelines: [],
  criteria: {
    filter: '',
    pageOffset: 0,
    pageSize: 10,
  },
};

const reducer = handleActions(
  {
    PIPELINES_RECEIVED: (state, action) => ({
      ...state,
      total: action.payload.total,
      pipelines: action.payload.pipelines,
    }),
    UPDATE_CRITERIA: (state, action) => ({
      ...state,
      criteria: action.payload.criteria,
    }),
  },
  defaultPipelineSearchState,
);

export { actionCreators, reducer };
