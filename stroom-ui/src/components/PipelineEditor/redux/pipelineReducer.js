import { combineActions, createActions, handleActions } from 'redux-actions';

import {
  moveElementInPipeline,
  removeElementFromPipeline,
  createNewElementInPipeline,
  reinstateElementToPipeline,
  setElementPropertyValueInPipeline,
  revertPropertyToParent,
  revertPropertyToDefault,
} from '../pipelineUtils';

import { createActionHandlerPerId } from 'lib/reduxFormUtils';
import { getPipelineAsTree } from '../pipelineUtils';

const actionCreators = createActions({
  PIPELINE_RECEIVED: (pipelineId, pipeline) => ({
    pipelineId,
    pipeline,
  }),
  PIPELINE_SAVE_REQUESTED: pipelineId => ({
    pipelineId,
  }),
  PIPELINE_SAVED: pipelineId => ({
    pipelineId,
  }),
  PIPELINE_SETTINGS_UPDATED: (pipelineId, description) => ({
    pipelineId,
    description,
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
  PIPELINE_ELEMENT_DELETE_REQUESTED: (pipelineId, elementId) => ({ pipelineId, elementId }),
  PIPELINE_ELEMENT_DELETE_CANCELLED: pipelineId => ({ pipelineId, elementId: undefined }),
  PIPELINE_ELEMENT_DELETED: (pipelineId, elementId) => ({ pipelineId, elementId }),
  PIPELINE_ELEMENT_REINSTATED: (pipelineId, parentId, recycleData) => ({
    pipelineId,
    parentId,
    recycleData,
  }),
  PIPELINE_ELEMENT_PROPERTY_UPDATED: (pipelineId, element, name, propertyType, propertyValue) => ({
    pipelineId,
    element,
    name,
    propertyType,
    propertyValue,
  }),
  PIPELINE_ELEMENT_PROPERTY_REVERT_TO_PARENT: (pipelineId, elementId, name) => ({
    pipelineId,
    elementId,
    name,
  }),
  PIPELINE_ELEMENT_PROPERTY_REVERT_TO_DEFAULT: (pipelineId, elementId, name) => ({
    pipelineId,
    elementId,
    name,
  }),
});

const { pipelineElementDeleteRequested, pipelineElementDeleteCancelled } = actionCreators;

// pipelines, keyed on ID, there may be several expressions on a page
const defaultState = {};

const defaultPipelineState = {
  isDirty: false,
  isSaving: false,
  pendingElementToDelete: undefined,
};

const updatePipeline = pipeline => ({
  pipeline,
  asTree: getPipelineAsTree(pipeline),
});

const byPipelineId = createActionHandlerPerId(
  ({ payload: { pipelineId } }) => pipelineId,
  defaultPipelineState,
);

const reducer = handleActions(
  {
    PIPELINE_RECEIVED: byPipelineId((state, action) => updatePipeline(action.payload.pipeline)),
    PIPELINE_SAVE_REQUESTED: byPipelineId((state, action) => ({
      isSaving: true,
    })),
    PIPELINE_SAVED: byPipelineId((state, action) => ({
      isDirty: false,
      isSaving: false,
    })),
    PIPELINE_SETTINGS_UPDATED: byPipelineId((state, { payload: { description } }, currentPipelineState) => ({
      pipeline: {
        ...currentPipelineState.pipeline,
        description,
      },
      isDirty: true,
    })),
    PIPELINE_ELEMENT_SELECTED: byPipelineId((state, { payload: { elementId, initialValues } }) => ({
      selectedElementId: elementId,
      selectedElementInitialValues: initialValues,
    })),
    [combineActions(pipelineElementDeleteRequested, pipelineElementDeleteCancelled)]: byPipelineId((state, { payload: { elementId } }) => ({
      pendingElementToDelete: elementId,
    })),
    PIPELINE_ELEMENT_DELETED: byPipelineId((state, { payload: { elementId } }, currentPipelineState) => ({
      ...updatePipeline(removeElementFromPipeline(currentPipelineState, elementId)),
      isDirty: true,
      pendingElementToDelete: undefined,
    })),
    PIPELINE_ELEMENT_REINSTATED: byPipelineId((state, { payload: { parentId, recycleData } }, currentPipelineState) => ({
      ...updatePipeline(reinstateElementToPipeline(currentPipelineState, parentId, recycleData)),
      isDirty: true,
    })),
    PIPELINE_ELEMENT_ADDED: byPipelineId((state, { payload: { parentId, childDefinition, name } }, currentPipelineState) => ({
      ...updatePipeline(createNewElementInPipeline(currentPipelineState, parentId, childDefinition, name)),
      isDirty: true,
    })),
    PIPELINE_ELEMENT_MOVED: byPipelineId((state, { payload: { itemToMove, destination } }, currentPipelineState) => ({
      ...updatePipeline(moveElementInPipeline(currentPipelineState, itemToMove, destination)),
      isDirty: true,
    })),
    PIPELINE_ELEMENT_PROPERTY_UPDATED: byPipelineId((
      state,
      {
        payload: {
          element, name, propertyType, propertyValue,
        },
      },
      currentPipelineState,
    ) => ({
      ...updatePipeline(setElementPropertyValueInPipeline(
        currentPipelineState,
        element,
        name,
        propertyType,
        propertyValue,
      )),
      isDirty: true,
    })),
    PIPELINE_ELEMENT_PROPERTY_REVERT_TO_PARENT: byPipelineId((state, { payload: { elementId, name } }, currentPipelineState) => ({
      ...updatePipeline(revertPropertyToParent(currentPipelineState, elementId, name)),
      isDirty: true,
    })),
    PIPELINE_ELEMENT_PROPERTY_REVERT_TO_DEFAULT: byPipelineId((state, { elementId, name }, currentPipelineState) => ({
      ...updatePipeline(revertPropertyToDefault(currentPipelineState, elementId, name)),
      isDirty: true,
    })),
  },
  defaultState,
);

export { actionCreators, reducer };
