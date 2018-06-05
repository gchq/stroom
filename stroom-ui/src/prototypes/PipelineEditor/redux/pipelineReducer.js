import { createAction, handleActions } from 'redux-actions';

import {
  moveElementInPipeline,
  deleteElementInPipeline,
  createNewElementInPipeline,
} from '../pipelineUtils';

const pipelineReceived = createAction('PIPELINE_RECEIVED', (pipelineId, pipeline) => ({
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

const pipelineElementAdded = createAction(
  'PIPELINE_ELEMENT_ADDED',
  (pipelineId, parentId, childDefinition, name) => ({
    pipelineId,
    parentId,
    childDefinition,
    name,
  }),
);

const requestDeletePipelineElement = createAction(
  'REQUEST_DELETE_PIPELINE_ELEMENT',
  (pipelineId, elementId) => ({ pipelineId, elementId }),
);

const confirmDeletePipelineElement = createAction(
  'CONFIRM_DELETE_PIPELINE_ELEMENT',
  (pipelineId, elementId) => ({ pipelineId, elementId }),
);

const cancelDeletePipelineElement = createAction('CANCEL_DELETE_PIPELINE_ELEMENT', pipelineId => ({
  pipelineId,
}));

const openPipelineElementContextMenu = createAction(
  'OPEN_PIPELINE_ELEMENT_CONTEXT_MENU',
  (pipelineId, elementId) => ({ pipelineId, elementId }),
);
const closePipelineElementContextMenu = createAction(
  'CLOSE_PIPELINE_ELEMENT_CONTEXT_MENU',
  (pipelineId, elementId) => ({ pipelineId, elementId }),
);

// pipelines, keyed on ID, there may be several expressions on a page
const defaultPipelineState = {
  selectedElementId: undefined,
  pendingElementIdToDelete: undefined,
  contextMenuElementId: undefined,
};

const pipelineReducer = handleActions(
  {
    [pipelineReceived]: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...defaultPipelineState,
        pipeline: action.payload.pipeline,
      },
    }),
    [pipelineElementSelected]: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...state[action.payload.pipelineId],
        selectedElementId: action.payload.elementId,
      },
    }),
    [requestDeletePipelineElement]: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...state[action.payload.pipelineId],
        pendingElementIdToDelete: action.payload.elementId,
      },
    }),
    [confirmDeletePipelineElement]: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        pipeline: deleteElementInPipeline(
          state[action.payload.pipelineId].pipeline,
          action.payload.elementId,
        ),
        pendingElementIdToDelete: undefined,
      },
    }),
    [cancelDeletePipelineElement]: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...state[action.payload.pipelineId],
        pendingElementIdToDelete: undefined,
      },
    }),
    [pipelineElementAdded]: (state, action) => ({
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
    [pipelineElementMoved]: (state, action) => ({
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
    [openPipelineElementContextMenu]: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...state[action.payload.pipelineId],
        contextMenuElementId: action.payload.elementId,
      },
    }),
    [closePipelineElementContextMenu]: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...state[action.payload.pipelineId],
        contextMenuElementId: undefined,
      },
    }),
  },
  defaultPipelineState,
);

export {
  pipelineReceived,
  pipelineElementSelected,
  requestDeletePipelineElement,
  confirmDeletePipelineElement,
  cancelDeletePipelineElement,
  pipelineElementAdded,
  pipelineElementMoved,
  openPipelineElementContextMenu,
  closePipelineElementContextMenu,
  pipelineReducer,
};
