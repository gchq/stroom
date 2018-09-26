import { Action, ActionCreator } from "redux";

import {
  prepareReducerById,
  StateById,
  ActionId
} from "../../../lib/redux-actions-ts";

import {
  PIPELINE_SETTINGS_UPDATED,
  PipelineSettingsUpdatedAction
} from "./pipelineStatesReducer";

export const PIPELINE_SETTINGS_OPENED = "PIPELINE_SETTINGS_OPENED";
export const PIPELINE_SETTINGS_CLOSED = "PIPELINE_SETTINGS_CLOSED";

export interface PipelineSettingsAction extends ActionId {
  isOpen: boolean;
}

export interface ActionCreators {
  pipelineSettingsOpened: ActionCreator<
    Action<"PIPELINE_SETTINGS_OPENED"> & PipelineSettingsAction
  >;
  pipelineSettingsClosed: ActionCreator<
    Action<"PIPELINE_SETTINGS_CLOSED"> & PipelineSettingsAction
  >;
}

export const actionCreators: ActionCreators = {
  pipelineSettingsOpened: id => ({
    type: PIPELINE_SETTINGS_OPENED,
    id,
    isOpen: true
  }),
  pipelineSettingsClosed: id => ({
    type: PIPELINE_SETTINGS_CLOSED,
    id,
    isOpen: false
  })
};

export interface StoreStatePerId {
  isOpen: boolean;
}

export interface StoreState extends StateById<StoreStatePerId> {}

const defaultStatePerId: StoreStatePerId = {
  isOpen: false
};

export const reducer = prepareReducerById(defaultStatePerId)
  .handleActions<PipelineSettingsAction>(
    [PIPELINE_SETTINGS_OPENED, PIPELINE_SETTINGS_CLOSED],
    (state, { isOpen }) => ({
      isOpen
    })
  )
  .handleForeignAction<PipelineSettingsUpdatedAction>(
    PIPELINE_SETTINGS_UPDATED,
    () => ({
      isOpen: false
    })
  )
  .getReducer();
