import { combineReducers } from 'redux';

import {
  actionCreators as elementActionCreators,
  reducer as elementReducer,
} from './elementReducer';

import { 
  actionCreators as pipelineSettingsActionCreators,
  reducer as pipelineSettingsReducer
} from './pipelineSettingsReducer';

import {
  actionCreators as pipelineActionCreators,
  reducer as pipelineReducer,
} from './pipelineReducer';

import {
  actionCreators as pipelineSearchActionCreators,
  reducer as pipelineSearchReducer,
} from './pipelineSearchReducer';

const actionCreators = {
  ...elementActionCreators,
  ...pipelineActionCreators,
  ...pipelineSearchActionCreators,
  ...pipelineSettingsActionCreators
};

const reducer = combineReducers({
  elements: elementReducer,
  pipelines: pipelineReducer,
  search: pipelineSearchReducer,
  settings: pipelineSettingsReducer
});

export { actionCreators, reducer };
