import { combineReducers } from 'redux';

import {
  actionCreators as elementActionCreators,
  reducer as elementReducer,
} from './elementReducer';

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
};

const reducer = combineReducers({
  elements: elementReducer,
  pipelines: pipelineReducer,
  search: pipelineSearchReducer,
});

export { actionCreators, reducer };
