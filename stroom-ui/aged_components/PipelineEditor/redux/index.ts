import { combineReducers } from 'redux';

import {
  actionCreators as inheritPipelineActionCreators,
  reducer as inheritPipelineReducer,
} from './inheritPipelineReducer';

import {
  actionCreators as elementActionCreators,
  reducer as elementReducer,
} from './elementReducer';

import {
  actionCreators as pipelineSettingsActionCreators,
  reducer as pipelineSettingsReducer,
} from './pipelineSettingsReducer';

import {
  actionCreators as pipelineActionCreators,
  reducer as pipelineStatesReducer,
} from './pipelineStatesReducer';

import {
  actionCreators as pipelineSearchActionCreators,
  reducer as pipelineSearchReducer,
} from './pipelineSearchReducer';

const actionCreators = {
  ...elementActionCreators,
  ...pipelineActionCreators,
  ...pipelineSearchActionCreators,
  ...pipelineSettingsActionCreators,
  ...inheritPipelineActionCreators,
};

const reducer = combineReducers({
  elements: elementReducer,
  pipelineStates: pipelineStatesReducer,
  search: pipelineSearchReducer,
  settings: pipelineSettingsReducer,
  inheritPipeline: inheritPipelineReducer,
});

export { actionCreators, reducer };
