import { createAction, handleActions, combineActions } from 'redux-actions';

import { getPipelineAsTree, moveElementInPipeline } from './pipelineUtils';

const pipelineChanged = createAction('PIPELINE_CHANGED', (pipelineId, pipeline) => ({
  pipelineId,
  pipeline,
}));

const pipelineElementSelected = createAction(
  'PIPELINE_ELEMENT_SELECTED',
  (pipelineId, elementId) => ({ pipelineId, elementId }),
);

const pipelineElementMoved = createAction(
  'PIPELINE_ELEMENT_MOVED',
  (pipelineId, itemToMove, destination) => ({ pipelineId, itemToMove, destination }),
);

// pipelines, keyed on ID, there may be several expressions on a page
const defaultPipelineState = {};

const pipelineReducer = handleActions(
  {
    [pipelineChanged]: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        pipeline: action.payload.pipeline,
        asTree: getPipelineAsTree(action.payload.pipeline),
        selected: undefined,
      },
    }),
    [pipelineElementSelected]: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...state[action.payload.pipelineId],
        selected: action.payload.elementId,
      },
    }),
    [pipelineElementMoved]: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {},
    }),
  },
  defaultPipelineState,
);

export { pipelineChanged, pipelineReducer };
