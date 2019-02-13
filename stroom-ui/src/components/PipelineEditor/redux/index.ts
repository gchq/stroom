import { combineReducers } from "redux";

import {
  actionCreators as elementActionCreators,
  reducer as elementReducer,
  StoreState as ElementStoreState
} from "./elementReducer";

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
}

export const actionCreators = {
  ...elementActionCreators,
  ...pipelineActionCreators,
  ...pipelineSearchActionCreators
};

export const reducer = combineReducers({
  elements: elementReducer,
  pipelineStates: pipelineStatesReducer,
  search: pipelineSearchReducer
});
