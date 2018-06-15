import { createActions, handleActions } from 'redux-actions';

import {
  moveElementInPipeline,
  deleteElementInPipeline,
  createNewElementInPipeline,
} from '../pipelineUtils';

import { getPipelineAsTree } from '../pipelineUtils';

const actionCreators = createActions({
  PIPELINE_RECEIVED: (pipelineId, pipeline) => ({
    pipelineId,
    pipeline,
  }),
  PIPELINE_ELEMENT_SELECTED: (pipelineId, elementId, initialValues) => ({
    pipelineId,
    elementId,
    initialValues,
  }),
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

const updatePipeline = pipeline => ({
  pipeline,
  asTree: getPipelineAsTree(pipeline),
});

const pipelineReducer = handleActions(
  {
    PIPELINE_RECEIVED: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...defaultPipelineState,
        ...updatePipeline(action.payload.pipeline),
      },
    }),
    PIPELINE_ELEMENT_SELECTED: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...state[action.payload.pipelineId],
        selectedElementId: action.payload.elementId,
        selectedElementInitialValues: action.payload.initialValues,
      },
    }),
    PIPELINE_ELEMENT_DELETED: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...updatePipeline(deleteElementInPipeline(
          state[action.payload.pipelineId].pipeline,
          action.payload.elementId,
        )),
      },
    }),
    PIPELINE_ELEMENT_ADDED: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...state[action.payload.pipelineId],
        ...updatePipeline(createNewElementInPipeline(
          state[action.payload.pipelineId].pipeline,
          action.payload.parentId,
          action.payload.childDefinition,
          action.payload.name,
        )),
      },
    }),
    PIPELINE_ELEMENT_MOVED: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...state[action.payload.pipelineId],
        ...updatePipeline(moveElementInPipeline(
          state[action.payload.pipelineId].pipeline,
          action.payload.itemToMove,
          action.payload.destination,
        )),
      },
    }),
  },
  defaultPipelineState,
);

export { actionCreators, pipelineReducer };
