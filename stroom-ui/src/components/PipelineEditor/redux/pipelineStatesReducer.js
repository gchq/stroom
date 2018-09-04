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
  PIPELINE_ELEMENT_ADD_REQUESTED: (pipelineId, parentId, elementDefinition) => ({
    pipelineId,
    parentId,
    elementDefinition,
  }),
  PIPELINE_ELEMENT_ADD_CANCELLED: pipelineId => ({
    pipelineId,
  }),
  PIPELINE_ELEMENT_ADD_CONFIRMED: (pipelineId, name) => ({
    pipelineId,
    name,
  }),
  PIPELINE_ELEMENT_DELETE_REQUESTED: (pipelineId, elementId) => ({ pipelineId, elementId }),
  PIPELINE_ELEMENT_DELETE_CANCELLED: pipelineId => ({ pipelineId, elementId: undefined }),
  PIPELINE_ELEMENT_DELETE_CONFIRMED: pipelineId => ({ pipelineId }),
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
  pendingNewElement: undefined,
  pendingElementIdToDelete: undefined,
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
    PIPELINE_SETTINGS_UPDATED: byPipelineId((state, { payload: { description } }, { pipeline }) => ({
      pipeline: {
        ...pipeline,
        description,
      },
      isDirty: true,
    })),
    PIPELINE_ELEMENT_SELECTED: byPipelineId((state, { payload: { elementId, initialValues } }) => ({
      selectedElementId: elementId,
      selectedElementInitialValues: initialValues,
    })),
    [combineActions(pipelineElementDeleteRequested, pipelineElementDeleteCancelled)]: byPipelineId((state, { payload: { elementId } }) => ({
      pendingElementIdToDelete: elementId,
    })),
    PIPELINE_ELEMENT_DELETE_CONFIRMED: byPipelineId((state, action, { pipeline, pendingElementIdToDelete }) => ({
      ...updatePipeline(removeElementFromPipeline(pipeline, pendingElementIdToDelete)),
      isDirty: true,
      pendingElementIdToDelete: undefined,
    })),
    PIPELINE_ELEMENT_REINSTATED: byPipelineId((state, { payload: { parentId, recycleData } }, { pipeline }) => ({
      ...updatePipeline(reinstateElementToPipeline(pipeline, parentId, recycleData)),
      isDirty: true,
    })),
    PIPELINE_ELEMENT_ADD_REQUESTED: byPipelineId((state, { payload: { parentId, elementDefinition } }) => ({
      pendingNewElement: {
        parentId,
        elementDefinition,
      },
    })),
    PIPELINE_ELEMENT_ADD_CANCELLED: byPipelineId((state, action, currentPipelineState) => ({
      pendingNewElement: undefined,
    })),
    PIPELINE_ELEMENT_ADD_CONFIRMED: byPipelineId((
      state,
      { payload: { name } },
      { pipeline, pendingNewElement: { parentId, elementDefinition } },
    ) => ({
      ...updatePipeline(createNewElementInPipeline(pipeline, parentId, elementDefinition, name)),
      pendingNewElement: undefined,
      isDirty: true,
    })),
    PIPELINE_ELEMENT_MOVED: byPipelineId((state, { payload: { itemToMove, destination } }, { pipeline }) => ({
      ...updatePipeline(moveElementInPipeline(pipeline, itemToMove, destination)),
      isDirty: true,
    })),
    PIPELINE_ELEMENT_PROPERTY_UPDATED: byPipelineId((state, {
      payload: {
        element, name, propertyType, propertyValue,
      },
    }, { pipeline }) => ({
      ...updatePipeline(setElementPropertyValueInPipeline(pipeline, element, name, propertyType, propertyValue)),
      isDirty: true,
    })),
    PIPELINE_ELEMENT_PROPERTY_REVERT_TO_PARENT: byPipelineId((state, { payload: { elementId, name } }, { pipeline }) => ({
      ...updatePipeline(revertPropertyToParent(pipeline, elementId, name)),
      isDirty: true,
    })),
    PIPELINE_ELEMENT_PROPERTY_REVERT_TO_DEFAULT: byPipelineId((state, { elementId, name }, { pipeline }) => ({
      ...updatePipeline(revertPropertyToDefault(pipeline, elementId, name)),
      isDirty: true,
    })),
  },
  defaultState,
);

export { actionCreators, reducer };
