import { Action, ActionCreator } from "redux";

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

export const PIPELINE_RECEIVED = "PIPELINE_RECEIVED";
export const PIPELINE_SAVE_REQUESTED = "PIPELINE_SAVE_REQUESTED";
export const PIPELINE_SAVED = "PIPELINE_SAVED";
export const PIPELINE_SETTINGS_UPDATED = "PIPELINE_SETTINGS_UPDATED";
export const PIPELINE_ELEMENT_SELECTED = "PIPELINE_ELEMENT_SELECTED";
export const PIPELINE_ELEMENT_SELECTION_CLEARED =
  "PIPELINE_ELEMENT_SELECTION_CLEARED";
export const PIPELINE_ELEMENT_MOVED = "PIPELINE_ELEMENT_MOVED";
export const PIPELINE_ELEMENT_ADD_REQUESTED = "PIPELINE_ELEMENT_ADD_REQUESTED";
export const PIPELINE_ELEMENT_ADD_CANCELLED = "PIPELINE_ELEMENT_ADD_CANCELLED";
export const PIPELINE_ELEMENT_ADD_CONFIRMED = "PIPELINE_ELEMENT_ADD_CONFIRMED";
export const PIPELINE_ELEMENT_DELETE_REQUESTED =
  "PIPELINE_ELEMENT_DELETE_REQUESTED";
export const PIPELINE_ELEMENT_DELETE_CANCELLED =
  "PIPELINE_ELEMENT_DELETE_CANCELLED";
export const PIPELINE_ELEMENT_DELETE_CONFIRMED =
  "PIPELINE_ELEMENT_DELETE_CONFIRMED";
export const PIPELINE_ELEMENT_REINSTATED = "PIPELINE_ELEMENT_REINSTATED";
export const PIPELINE_ELEMENT_PROPERTY_UPDATED =
  "PIPELINE_ELEMENT_PROPERTY_UPDATED";
export const PIPELINE_ELEMENT_PROPERTY_REVERT_TO_PARENT =
  "PIPELINE_ELEMENT_PROPERTY_REVERT_TO_PARENT";
export const PIPELINE_ELEMENT_PROPERTY_REVERT_TO_DEFAULT =
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
export interface PipelineElementAddRequestedAction
  extends Action<"PIPELINE_ELEMENT_ADD_REQUESTED">,
    ActionId {
  parentId: string;
  elementDefinition: ElementDefinition;
}
export interface PipelineElementAddCancelledAction
  extends Action<"PIPELINE_ELEMENT_ADD_CANCELLED">,
    ActionId {}
export interface PipelineElementAddConfirmedAction
  extends Action<"PIPELINE_ELEMENT_ADD_CONFIRMED">,
    ActionId {
  name: string;
}
export interface PipelineElementDeleteRequestedAction
  extends Action<"PIPELINE_ELEMENT_DELETE_REQUESTED">,
    ActionId {
  elementId: string;
}
export interface PipelineElementDeleteCancelledAction
  extends Action<"PIPELINE_ELEMENT_DELETE_CANCELLED">,
    ActionId {}
export interface PipelineElementDeleteConfirmedAction
  extends Action<"PIPELINE_ELEMENT_DELETE_CONFIRMED">,
    ActionId {}
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

export interface ActionCreators {
  pipelineReceived: ActionCreator<PipelineReceivedAction>;
  pipelineSaveRequested: ActionCreator<PipelineSaveRequestedAction>;
  pipelineSaved: ActionCreator<PipelineSavedAction>;
  pipelineSettingsUpdated: ActionCreator<PipelineSettingsUpdatedAction>;
  pipelineElementSelected: ActionCreator<PipelineElementSelectedAction>;
  pipelineElementSelectionCleared: ActionCreator<
    PipelineElementSelectionCleared
  >;
  pipelineElementMoved: ActionCreator<PipelineElementMovedAction>;
  pipelineElementAddRequested: ActionCreator<PipelineElementAddRequestedAction>;
  pipelineElementAddCancelled: ActionCreator<PipelineElementAddCancelledAction>;
  pipelineElementAddConfirmed: ActionCreator<PipelineElementAddConfirmedAction>;
  pipelineElementDeleteRequested: ActionCreator<
    PipelineElementDeleteRequestedAction
  >;
  pipelineElementDeleteCancelled: ActionCreator<
    PipelineElementDeleteCancelledAction
  >;
  pipelineElementDeleteConfirmed: ActionCreator<
    PipelineElementDeleteConfirmedAction
  >;
  pipelineElementReinstated: ActionCreator<PipelineElementReinstatedAction>;
  pipelineElementPropertyUpdated: ActionCreator<
    PipelineElementPropertyUpdatedAction
  >;
  pipelineElementPropertyRevertToParent: ActionCreator<
    PipelineElementPropertyRevertToParentAction
  >;
  pipelineElementPropertyRevertToDefault: ActionCreator<
    PipelineElementPropertyRevertToDefaultAction
  >;
}

export const actionCreators: ActionCreators = {
  pipelineReceived: (id, pipeline) => ({
    type: PIPELINE_RECEIVED,
    id,
    pipeline
  }),
  pipelineSaveRequested: id => ({
    type: PIPELINE_SAVE_REQUESTED,
    id
  }),
  pipelineSaved: id => ({
    type: PIPELINE_SAVED,
    id
  }),
  pipelineSettingsUpdated: (id, description) => ({
    type: PIPELINE_SETTINGS_UPDATED,
    id,
    description
  }),
  pipelineElementSelected: (id, elementId, initialValues) => ({
    type: PIPELINE_ELEMENT_SELECTED,
    id,
    elementId,
    initialValues
  }),
  pipelineElementSelectionCleared: id => ({
    type: PIPELINE_ELEMENT_SELECTION_CLEARED,
    id
  }),
  pipelineElementMoved: (id, itemToMove, destination) => ({
    type: PIPELINE_ELEMENT_MOVED,
    id,
    itemToMove,
    destination
  }),
  pipelineElementAddRequested: (id, parentId, elementDefinition) => ({
    type: PIPELINE_ELEMENT_ADD_REQUESTED,
    id,
    parentId,
    elementDefinition
  }),
  pipelineElementAddCancelled: id => ({
    type: PIPELINE_ELEMENT_ADD_CANCELLED,
    id
  }),
  pipelineElementAddConfirmed: (id, name) => ({
    type: PIPELINE_ELEMENT_ADD_CONFIRMED,
    id,
    name
  }),
  pipelineElementDeleteRequested: (id, elementId) => ({
    type: PIPELINE_ELEMENT_DELETE_REQUESTED,
    id,
    elementId
  }),
  pipelineElementDeleteCancelled: id => ({
    type: PIPELINE_ELEMENT_DELETE_CANCELLED,
    id
  }),
  pipelineElementDeleteConfirmed: id => ({
    type: PIPELINE_ELEMENT_DELETE_CONFIRMED,
    id
  }),
  pipelineElementReinstated: (id, parentId, recycleData) => ({
    type: PIPELINE_ELEMENT_REINSTATED,
    id,
    parentId,
    recycleData
  }),
  pipelineElementPropertyUpdated: (
    id,
    element,
    name,
    propertyType,
    propertyValue
  ) => ({
    type: PIPELINE_ELEMENT_PROPERTY_UPDATED,
    id,
    element,
    name,
    propertyType,
    propertyValue
  }),
  pipelineElementPropertyRevertToParent: (id, elementId, name) => ({
    type: PIPELINE_ELEMENT_PROPERTY_REVERT_TO_PARENT,
    id,
    elementId,
    name
  }),
  pipelineElementPropertyRevertToDefault: (id, elementId, name) => ({
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
  pendingNewElement?: {
    parentId: string;
    elementDefinition: ElementDefinition;
  };
  pendingElementIdToDelete?: string;
  selectedElementId?: string;
  selectedElementInitialValues?: object;
}

export interface StoreState extends StateById<StoreStateById> {}

export const defaultStatePerId: StoreStateById = {
  isDirty: false,
  isSaving: false,
  pendingNewElement: undefined,
  pendingElementIdToDelete: undefined
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
  .handleAction<PipelineElementDeleteRequestedAction>(
    PIPELINE_ELEMENT_DELETE_REQUESTED,
    (state = defaultStatePerId, { elementId }) => ({
      ...state,
      pendingElementIdToDelete: elementId
    })
  )
  .handleAction<PipelineElementDeleteCancelledAction>(
    PIPELINE_ELEMENT_DELETE_CANCELLED,
    (state = defaultStatePerId) => ({
      ...state,
      pendingElementIdToDelete: undefined
    })
  )
  .handleAction<PipelineElementDeleteConfirmedAction>(
    PIPELINE_ELEMENT_DELETE_CONFIRMED,
    (state = defaultStatePerId) => ({
      ...state,
      ...updatePipeline(
        removeElementFromPipeline(
          state.pipeline!,
          state.pendingElementIdToDelete!
        )
      ),
      isDirty: true,
      pendingElementIdToDelete: undefined
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
  .handleAction<PipelineElementAddRequestedAction>(
    PIPELINE_ELEMENT_ADD_REQUESTED,
    (state = defaultStatePerId, { parentId, elementDefinition }) => ({
      ...state,
      pendingNewElement: {
        parentId,
        elementDefinition
      }
    })
  )
  .handleAction<PipelineElementAddCancelledAction>(
    PIPELINE_ELEMENT_ADD_CANCELLED,
    (state = defaultStatePerId) => ({
      ...state,
      pendingNewElement: undefined
    })
  )
  .handleAction<PipelineElementAddConfirmedAction>(
    PIPELINE_ELEMENT_ADD_CONFIRMED,
    (state = defaultStatePerId, { name }) => ({
      ...state,
      ...updatePipeline(
        createNewElementInPipeline(
          state.pipeline!,
          state.pendingNewElement!.parentId,
          state.pendingNewElement!.elementDefinition,
          name
        )
      ),
      pendingNewElement: undefined,
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
