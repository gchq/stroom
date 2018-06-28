import { createActions, handleActions } from 'redux-actions';

import {
  moveElementInPipeline,
  deleteElementInPipeline,
  createNewElementInPipeline,
} from '../pipelineUtils';

const actionCreators = createActions({
  PIPELINE_RECEIVED: (pipelineId, pipeline) => ({
    pipelineId,
    pipeline,
  }),
  PIPELINE_ELEMENT_SELECTED: (pipelineId, elementId) => ({ pipelineId, elementId }),
  PIPELINE_ELEMENT_MOVED: (pipelineId, itemToMove, destination) => ({
    pipelineId,
    itemToMove,
    destination,
  }),
  PIPELINE_ELEMENT_ADDED: (pipelineId, parentId, childDefinition, name) => ({
    pipelineId,
    parentId,
    childDefinition,
    name,
  }),
  PIPELINE_ELEMENT_DELETED: (pipelineId, elementId) => ({ pipelineId, elementId }),
});

// pipelines, keyed on ID, there may be several expressions on a page
const defaultPipelineState = {};

const pipelineReducer = handleActions(
  {
    PIPELINE_RECEIVED: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...defaultPipelineState,
        pipeline: action.payload.pipeline,
      },
    }),
    PIPELINE_ELEMENT_SELECTED: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...state[action.payload.pipelineId],
        selectedElementId: action.payload.elementId,
      },
    }),
    PIPELINE_ELEMENT_DELETED: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        pipeline: deleteElementInPipeline(
          state[action.payload.pipelineId].pipeline,
          action.payload.elementId,
        ),
      },
    }),
    PIPELINE_ELEMENT_ADDED: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...state[action.payload.pipelineId],
        pipeline: createNewElementInPipeline(
          state[action.payload.pipelineId].pipeline,
          action.payload.parentId,
          action.payload.childDefinition,
          action.payload.name,
        ),
      },
    }),
    PIPELINE_ELEMENT_MOVED: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...state[action.payload.pipelineId],
        pipeline: moveElementInPipeline(
          state[action.payload.pipelineId].pipeline,
          action.payload.itemToMove,
          action.payload.destination,
        ),
      },
    }),
  },
  defaultPipelineState,
);

export { actionCreators, pipelineReducer };
