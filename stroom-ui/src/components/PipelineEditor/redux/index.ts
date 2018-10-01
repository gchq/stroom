import { combineReducers } from "redux";

import {
  actionCreators as inheritPipelineActionCreators,
  reducer as inheritPipelineReducer,
  StoreState as InheritPipelineStoreState
} from "./inheritPipelineReducer";

import {
  actionCreators as elementActionCreators,
  reducer as elementReducer,
  StoreState as ElementStoreState
} from "./elementReducer";

import {
  actionCreators as pipelineSettingsActionCreators,
  reducer as pipelineSettingsReducer,
  StoreState as PipelineSettingsStoreState
} from "./pipelineSettingsReducer";

import {
  actionCreators as pipelineActionCreators,
  reducer as pipelineStatesReducer,
  StoreState as PipelineStatesStoreState
} from "./pipelineStatesReducer";

import {
  actionCreators as pipelineSearchActionCreators,
  reducer as pipelineSearchReducer,
  StoreState as PipelineSearchStoreState
} from "./pipelineSearchReducer";

export interface StoreState {
  elements: ElementStoreState;
  pipelineStates: PipelineStatesStoreState;
  search: PipelineSearchStoreState;
  settings: PipelineSettingsStoreState;
  inheritPipeline: InheritPipelineStoreState;
}

export const actionCreators = {
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
