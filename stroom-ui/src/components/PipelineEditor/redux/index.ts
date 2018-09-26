import { combineReducers } from "redux";

import {
  actionCreators as inheritPipelineActionCreators,
  reducer as inheritPipelineReducer,
  StoreState as InheritPipelineStoreState,
  ActionCreators as InheritPipelineActionCreators
} from "./inheritPipelineReducer";

import {
  actionCreators as elementActionCreators,
  reducer as elementReducer,
  StoreState as ElementStoreState,
  ActionCreators as ElementActionCreators
} from "./elementReducer";

import {
  actionCreators as pipelineSettingsActionCreators,
  reducer as pipelineSettingsReducer,
  StoreState as PipelineSettingsStoreState,
  ActionCreators as PipelineSettingsActionCreators
} from "./pipelineSettingsReducer";

import {
  actionCreators as pipelineActionCreators,
  reducer as pipelineStatesReducer,
  StoreState as PipelineStatesStoreState,
  ActionCreators as PipelineStatesActionCreators
} from "./pipelineStatesReducer";

import {
  actionCreators as pipelineSearchActionCreators,
  reducer as pipelineSearchReducer,
  StoreState as PipelineSearchStoreState,
  ActionCreators as PipelineSearchActionCreators
} from "./pipelineSearchReducer";

export interface StoreState {
  elements: ElementStoreState;
  pipelineStates: PipelineStatesStoreState;
  search: PipelineSearchStoreState;
  settings: PipelineSettingsStoreState;
  inheritPipeline: InheritPipelineStoreState;
}

export interface ActionCreators
  extends ElementActionCreators,
    PipelineStatesActionCreators,
    PipelineSearchActionCreators,
    PipelineSettingsActionCreators,
    InheritPipelineActionCreators {}

export const actionCreators: ActionCreators = {
  ...elementActionCreators,
  ...pipelineActionCreators,
  ...pipelineSearchActionCreators,
  ...pipelineSettingsActionCreators,
  ...inheritPipelineActionCreators
};

export const reducer = combineReducers({
  elements: elementReducer,
  pipelineStates: pipelineStatesReducer,
  search: pipelineSearchReducer,
  settings: pipelineSettingsReducer,
  inheritPipeline: inheritPipelineReducer
});
