import { Action } from "redux";

import {
  prepareReducerById,
  StateById,
  ActionId
} from "../../../lib/redux-actions-ts";

import {
  moveElementInPipeline,
  removeElementFromPipeline,
  createNewElementInPipeline,
  reinstateElementToPipeline,
  setElementPropertyValueInPipeline,
  revertPropertyToParent,
  revertPropertyToDefault
} from "../pipelineUtils";

import { getPipelineAsTree } from "../pipelineUtils";
import {
  PipelineModelType,
  PipelineAsTreeType,
  ElementDefinition,
  PipelineElementType
} from "../../../types";

const PIPELINE_RECEIVED = "PIPELINE_RECEIVED";
const PIPELINE_SAVE_REQUESTED = "PIPELINE_SAVE_REQUESTED";
const PIPELINE_SAVED = "PIPELINE_SAVED";
const PIPELINE_SETTINGS_UPDATED = "PIPELINE_SETTINGS_UPDATED";
const PIPELINE_ELEMENT_SELECTED = "PIPELINE_ELEMENT_SELECTED";
const PIPELINE_ELEMENT_SELECTION_CLEARED = "PIPELINE_ELEMENT_SELECTION_CLEARED";
const PIPELINE_ELEMENT_MOVED = "PIPELINE_ELEMENT_MOVED";
const PIPELINE_ELEMENT_ADDED = "PIPELINE_ELEMENT_ADDED";
const PIPELINE_ELEMENT_DELETED = "PIPELINE_ELEMENT_DELETED";
const PIPELINE_ELEMENT_REINSTATED = "PIPELINE_ELEMENT_REINSTATED";
const PIPELINE_ELEMENT_PROPERTY_UPDATED = "PIPELINE_ELEMENT_PROPERTY_UPDATED";
const PIPELINE_ELEMENT_PROPERTY_REVERT_TO_PARENT =
  "PIPELINE_ELEMENT_PROPERTY_REVERT_TO_PARENT";
const PIPELINE_ELEMENT_PROPERTY_REVERT_TO_DEFAULT =
  "PIPELINE_ELEMENT_PROPERTY_REVERT_TO_DEFAULT";

export interface PipelineReceivedAction
  extends Action<"PIPELINE_RECEIVED">,
    ActionId {
  pipeline: PipelineModelType;
}
export interface PipelineSaveRequestedAction
  extends Action<"PIPELINE_SAVE_REQUESTED">,
    ActionId {}
export interface PipelineSavedAction
  extends Action<"PIPELINE_SAVED">,
    ActionId {}
export interface PipelineSettingsUpdatedAction
  extends Action<"PIPELINE_SETTINGS_UPDATED">,
    ActionId {
  description: string;
}
export interface PipelineElementSelectedAction
  extends Action<"PIPELINE_ELEMENT_SELECTED">,
    ActionId {
  elementId: string;
  initialValues: object; // TODO
}
export interface PipelineElementSelectionCleared
  extends Action<"PIPELINE_ELEMENT_SELECTION_CLEARED">,
    ActionId {}
export interface PipelineElementMovedAction
  extends Action<"PIPELINE_ELEMENT_MOVED">,
    ActionId {
  itemToMove: string;
  destination: string;
}
export interface PipelineElementAddedAction
  extends Action<"PIPELINE_ELEMENT_ADDED">,
    ActionId {
  parentId: string;
  elementDefinition: ElementDefinition;
  name: string;
}
export interface PipelineElementDeletedAction
  extends Action<"PIPELINE_ELEMENT_DELETED">,
    ActionId {
  elementId: string;
}
export interface PipelineElementReinstatedAction
  extends Action<"PIPELINE_ELEMENT_REINSTATED">,
    ActionId {
  parentId: string;
  recycleData: PipelineElementType;
}
export interface PipelineElementPropertyUpdatedAction
  extends Action<"PIPELINE_ELEMENT_PROPERTY_UPDATED">,
    ActionId {
  element: string;
  name: string;
  propertyType: string;
  propertyValue: any;
}
export interface PipelineElementPropertyRevertToParentAction
  extends Action<"PIPELINE_ELEMENT_PROPERTY_REVERT_TO_PARENT">,
    ActionId {
  elementId: string;
  name: string;
}
export interface PipelineElementPropertyRevertToDefaultAction
  extends Action<"PIPELINE_ELEMENT_PROPERTY_REVERT_TO_DEFAULT">,
    ActionId {
  elementId: string;
  name: string;
}

export const actionCreators = {
  pipelineReceived: (
    id: string,
    pipeline: PipelineModelType
  ): PipelineReceivedAction => ({
    type: PIPELINE_RECEIVED,
    id,
    pipeline
  }),
  pipelineSaveRequested: (id: string): PipelineSaveRequestedAction => ({
    type: PIPELINE_SAVE_REQUESTED,
    id
  }),
  pipelineSaved: (id: string): PipelineSavedAction => ({
    type: PIPELINE_SAVED,
    id
  }),
  pipelineSettingsUpdated: (
    id: string,
    description: string
  ): PipelineSettingsUpdatedAction => ({
    type: PIPELINE_SETTINGS_UPDATED,
    id,
    description
  }),
  pipelineElementSelected: (
    id: string,
    elementId: string,
    initialValues: object
  ): PipelineElementSelectedAction => ({
    type: PIPELINE_ELEMENT_SELECTED,
    id,
    elementId,
    initialValues
  }),
  pipelineElementSelectionCleared: (
    id: string
  ): PipelineElementSelectionCleared => ({
    type: PIPELINE_ELEMENT_SELECTION_CLEARED,
    id
  }),
  pipelineElementMoved: (
    id: string,
    itemToMove: string,
    destination: string
  ): PipelineElementMovedAction => ({
    type: PIPELINE_ELEMENT_MOVED,
    id,
    itemToMove,
    destination
  }),
  pipelineElementAdded: (
    id: string,
    parentId: string,
    elementDefinition: ElementDefinition,
    name: string
  ): PipelineElementAddedAction => ({
    type: PIPELINE_ELEMENT_ADDED,
    id,
    name,
    parentId,
    elementDefinition
  }),
  pipelineElementDeleted: (
    id: string,
    elementId: string
  ): PipelineElementDeletedAction => ({
    type: PIPELINE_ELEMENT_DELETED,
    id,
    elementId
  }),
  pipelineElementReinstated: (
    id: string,
    parentId: string,
    recycleData: PipelineElementType
  ): PipelineElementReinstatedAction => ({
    type: PIPELINE_ELEMENT_REINSTATED,
    id,
    parentId,
    recycleData
  }),
  pipelineElementPropertyUpdated: (
    id: string,
    element: string,
    name: string,
    propertyType: string,
    propertyValue: any
  ): PipelineElementPropertyUpdatedAction => ({
    type: PIPELINE_ELEMENT_PROPERTY_UPDATED,
    id,
    element,
    name,
    propertyType,
    propertyValue
  }),
  pipelineElementPropertyRevertToParent: (
    id: string,
    elementId: string,
    name: string
  ): PipelineElementPropertyRevertToParentAction => ({
    type: PIPELINE_ELEMENT_PROPERTY_REVERT_TO_PARENT,
    id,
    elementId,
    name
  }),
  pipelineElementPropertyRevertToDefault: (
    id: string,
    elementId: string,
    name: string
  ): PipelineElementPropertyRevertToDefaultAction => ({
    type: PIPELINE_ELEMENT_PROPERTY_REVERT_TO_DEFAULT,
    id,
    elementId,
    name
  })
};

export interface PipelineWithTree {
  pipeline?: PipelineModelType;
  asTree?: PipelineAsTreeType;
}

// pipelines, keyed on ID, there may be several expressions on a page
export interface StoreStateById extends PipelineWithTree {
  isDirty: boolean;
  isSaving: boolean;
  selectedElementId?: string;
  selectedElementInitialValues?: object;
}

export interface StoreState extends StateById<StoreStateById> {}

export const defaultStatePerId: StoreStateById = {
  isDirty: false,
  isSaving: false
};

const updatePipeline = (pipeline: PipelineModelType): PipelineWithTree => ({
  pipeline,
  asTree: getPipelineAsTree(pipeline)
});

export const reducer = prepareReducerById(defaultStatePerId)
  .handleAction<PipelineReceivedAction>(
    PIPELINE_RECEIVED,
    (state = defaultStatePerId, { pipeline }) => ({
      ...state,
      ...updatePipeline(pipeline)
    })
  )
  .handleAction<PipelineSaveRequestedAction>(
    PIPELINE_SAVE_REQUESTED,
    (state = defaultStatePerId) => ({
      ...state,
      isSaving: true
    })
  )
  .handleAction<PipelineSavedAction>(
    PIPELINE_SAVED,
    (state = defaultStatePerId) => ({
      ...state,
      isDirty: false,
      isSaving: false
    })
  )
  .handleAction<PipelineSettingsUpdatedAction>(
    PIPELINE_SETTINGS_UPDATED,
    (state = defaultStatePerId, { description }) => ({
      ...state,
      pipeline: {
        ...state.pipeline!,
        description
      },
      isDirty: true
    })
  )
  .handleAction<PipelineElementSelectedAction>(
    PIPELINE_ELEMENT_SELECTED,
    (state = defaultStatePerId, { elementId, initialValues }) => ({
      ...state,
      selectedElementId: elementId,
      selectedElementInitialValues: initialValues
    })
  )
  .handleAction<PipelineElementSelectionCleared>(
    PIPELINE_ELEMENT_SELECTION_CLEARED,
    (state = defaultStatePerId) => ({
      ...state,
      selectedElementId: undefined,
      selectedElementInitialValues: {}
    })
  )
  .handleAction<PipelineElementDeletedAction>(
    PIPELINE_ELEMENT_DELETED,
    (state = defaultStatePerId, { elementId }) => ({
      ...state,
      ...updatePipeline(removeElementFromPipeline(state.pipeline!, elementId)),
      isDirty: true
    })
  )
  .handleAction<PipelineElementReinstatedAction>(
    PIPELINE_ELEMENT_REINSTATED,
    (state = defaultStatePerId, { parentId, recycleData }) => ({
      ...state,
      ...updatePipeline(
        reinstateElementToPipeline(state.pipeline!, parentId, recycleData)
      ),
      isDirty: true
    })
  )
  .handleAction<PipelineElementAddedAction>(
    PIPELINE_ELEMENT_ADDED,
    (state = defaultStatePerId, { name, parentId, elementDefinition }) => ({
      ...state,
      ...updatePipeline(
        createNewElementInPipeline(
          state.pipeline!,
          parentId,
          elementDefinition,
          name
        )
      ),
      isDirty: true
    })
  )
  .handleAction<PipelineElementMovedAction>(
    PIPELINE_ELEMENT_MOVED,
    (state = defaultStatePerId, { itemToMove, destination }) => ({
      ...state,
      ...updatePipeline(
        moveElementInPipeline(state.pipeline!, itemToMove, destination)
      ),
      isDirty: true
    })
  )
  .handleAction<PipelineElementPropertyUpdatedAction>(
    PIPELINE_ELEMENT_PROPERTY_UPDATED,
    (
      state = defaultStatePerId,
      { element, name, propertyType, propertyValue }
    ) => ({
      ...state,
      ...updatePipeline(
        setElementPropertyValueInPipeline(
          state.pipeline!,
          element,
          name,
          propertyType,
          propertyValue
        )
      ),
      isDirty: true
    })
  )
  .handleAction<PipelineElementPropertyRevertToParentAction>(
    PIPELINE_ELEMENT_PROPERTY_REVERT_TO_PARENT,
    (state = defaultStatePerId, { elementId, name }) => ({
      ...state,
      ...updatePipeline(
        revertPropertyToParent(state.pipeline!, elementId, name)
      ),
      isDirty: true
    })
  )
  .handleAction<PipelineElementPropertyRevertToDefaultAction>(
    PIPELINE_ELEMENT_PROPERTY_REVERT_TO_DEFAULT,
    (state = defaultStatePerId, { elementId, name }) => ({
      ...state,
      ...updatePipeline(
        revertPropertyToDefault(state.pipeline!, elementId, name)
      ),
      isDirty: true
    })
  )
  .getReducer();
