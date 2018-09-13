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

import { createActionHandlersPerId } from 'lib/reduxFormUtils';
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
  SELECT_NEXT_PIPELINE_ELEMENT: (pipelineId, currentElementId) => ({
    pipelineId,
    currentElementId,
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

const byPipelineId = createActionHandlersPerId(
  ({ payload: { pipelineId } }) => pipelineId,
  defaultPipelineState,
);

const reducer = handleActions(
  byPipelineId({
    PIPELINE_RECEIVED: (state, { payload: { pipeline } }) =>
      updatePipeline(pipeline),
    PIPELINE_SAVE_REQUESTED: (state, action) => ({
      isSaving: true,
    }),
    PIPELINE_SAVED: (state, action) => ({
      isDirty: false,
      isSaving: false,
    }),
    PIPELINE_SETTINGS_UPDATED: (state, { payload: { description } }, { pipeline }) => ({
      pipeline: {
        ...pipeline,
        description,
      },
      isDirty: true,
    }),
    PIPELINE_ELEMENT_SELECTED: (state, { payload: { elementId, initialValues } }) => ({
      selectedElementId: elementId,
      selectedElementInitialValues: initialValues,
    }),
    SELECT_NEXT_PIPELINE_ELEMENT: (state, { payload: { currentElementId } }) => {
      //TODO: actually select the next element.
      //TODO: add SELECT_PREVIOUS_PIPELINE_ELEMENT
      return ({
        selectedElementId: 'Source'
      })
    },
    [combineActions(pipelineElementDeleteRequested, pipelineElementDeleteCancelled)]: (
      state,
      { payload: { elementId } },
    ) => ({
      pendingElementIdToDelete: elementId,
    }),
    PIPELINE_ELEMENT_DELETE_CONFIRMED: (state, action, { pipeline, pendingElementIdToDelete }) => ({
      ...updatePipeline(removeElementFromPipeline(pipeline, pendingElementIdToDelete)),
      isDirty: true,
      pendingElementIdToDelete: undefined,
    }),
    PIPELINE_ELEMENT_REINSTATED: (state, { payload: { parentId, recycleData } }, { pipeline }) => ({
      ...updatePipeline(reinstateElementToPipeline(pipeline, parentId, recycleData)),
      isDirty: true,
    }),
    PIPELINE_ELEMENT_ADD_REQUESTED: (state, { payload: { parentId, elementDefinition } }) => ({
      pendingNewElement: {
        parentId,
        elementDefinition,
      },
    }),
    PIPELINE_ELEMENT_ADD_CANCELLED: (state, action, currentPipelineState) => ({
      pendingNewElement: undefined,
    }),
    PIPELINE_ELEMENT_ADD_CONFIRMED: (
      state,
      { payload: { name } },
      { pipeline, pendingNewElement: { parentId, elementDefinition } },
    ) => ({
      ...updatePipeline(createNewElementInPipeline(pipeline, parentId, elementDefinition, name)),
      pendingNewElement: undefined,
      isDirty: true,
    }),
    PIPELINE_ELEMENT_MOVED: (state, { payload: { itemToMove, destination } }, { pipeline }) => ({
      ...updatePipeline(moveElementInPipeline(pipeline, itemToMove, destination)),
      isDirty: true,
    }),
    PIPELINE_ELEMENT_PROPERTY_UPDATED: (
      state,
      {
        payload: {
          element, name, propertyType, propertyValue,
        },
      },
      { pipeline },
    ) => ({
      ...updatePipeline(setElementPropertyValueInPipeline(pipeline, element, name, propertyType, propertyValue)),
      isDirty: true,
    }),
    PIPELINE_ELEMENT_PROPERTY_REVERT_TO_PARENT: (
      state,
      { payload: { elementId, name } },
      { pipeline },
    ) => ({
      ...updatePipeline(revertPropertyToParent(pipeline, elementId, name)),
      isDirty: true,
    }),
    PIPELINE_ELEMENT_PROPERTY_REVERT_TO_DEFAULT: (state, { elementId, name }, { pipeline }) => ({
      ...updatePipeline(revertPropertyToDefault(pipeline, elementId, name)),
      isDirty: true,
    }),
  }),
  defaultState,
);

export { actionCreators, reducer };
