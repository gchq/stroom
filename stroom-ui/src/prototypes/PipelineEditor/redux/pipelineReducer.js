import { createActions, handleActions } from 'redux-actions';

import {
  moveElementInPipeline,
  removeElementFromPipeline,
  createNewElementInPipeline,
  reinstateElementToPipeline,
} from '../pipelineUtils';

import { getPipelineAsTree } from '../pipelineUtils';

const actionCreators = createActions({
  PIPELINE_RECEIVED: (pipelineId, pipeline) => ({
    pipelineId,
    pipeline,
  }),
  PIPELINE_SAVED: pipelineId => ({
    pipelineId,
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
  PIPELINE_ELEMENT_REINSTATED: (pipelineId, parentId, recycleData) => ({
    pipelineId,
    parentId,
    recycleData,
  }),
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
        isDirty: false,
      },
    }),
    PIPELINE_SAVED: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...state[action.payload.pipelineId],
        isDirty: false,
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
        ...updatePipeline(removeElementFromPipeline(
          state[action.payload.pipelineId].pipeline,
          action.payload.elementId,
        )),
        isDirty: true,
      },
    }),
    PIPELINE_ELEMENT_REINSTATED: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...state[action.payload.pipelineId],
        ...updatePipeline(reinstateElementToPipeline(
          state[action.payload.pipelineId].pipeline,
          action.payload.parentId,
          action.payload.recycleData,
        )),
        isDirty: true,
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
        isDirty: true,
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
        isDirty: true,
      },
    }),
  },
  defaultPipelineState,
);

export { actionCreators, pipelineReducer };
