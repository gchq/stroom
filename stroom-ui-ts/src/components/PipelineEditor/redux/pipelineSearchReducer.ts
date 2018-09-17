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
    PIPELINES_RECEIVED: (state, { payload: { total, pipelines } }) => ({
      ...state,
      total,
      pipelines,
    }),
    UPDATE_CRITERIA: (state, { payload: { criteria } }) => ({
      ...state,
      criteria,
    }),
  },
  defaultPipelineSearchState,
);

export { actionCreators, reducer };
