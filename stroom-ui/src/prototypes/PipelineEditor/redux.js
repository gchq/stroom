import { createAction, handleActions, combineActions } from 'redux-actions';

import { moveElementInPipeline } from './pipelineUtils';

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

const openPipelineElementContextMenu = createAction(
  'OPEN_PIPELINE_ELEMENT_CONTEXT_MENU',
  (pipelineId, elementId) => ({pipelineId, elementId})
)
const closePipelineElementContextMenu = createAction(
  'CLOSE_PIPELINE_ELEMENT_CONTEXT_MENU',
  (pipelineId, elementId) => ({pipelineId, elementId})
)

// pipelines, keyed on ID, there may be several expressions on a page
const defaultPipelineState = {};

const pipelineReducer = handleActions(
  {
    [pipelineChanged]: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        pipeline: action.payload.pipeline,
        selectedElementId: undefined,
      },
    }),
    [pipelineElementSelected]: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...state[action.payload.pipelineId],
        selectedElementId: action.payload.elementId,
      },
    }),
    [pipelineElementMoved]: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...state[action.payload.pipelineId],
        pipeline: moveElementInPipeline(
          state[action.payload.pipelineId].pipeline,
          action.payload.itemToMove,
          action.payload.destination)
      },
    }),
    [openPipelineElementContextMenu]: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...state[action.payload.pipelineId],
        contextMenuElementId : action.payload.elementId
      }
    }),
    [closePipelineElementContextMenu]: (state, action) => ({
      ...state,
      [action.payload.pipelineId]: {
        ...state[action.payload.pipelineId],
        contextMenuElementId : undefined
      }
    })
  },
  defaultPipelineState,
);

export {
  pipelineChanged,
  pipelineElementSelected,
  pipelineElementMoved,
  openPipelineElementContextMenu,
  closePipelineElementContextMenu,
  pipelineReducer
};
