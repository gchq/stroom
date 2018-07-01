import { combineReducers } from 'redux';

import {
  actionCreators as elementActionCreators,
  reducer as elementReducer,
} from './elementReducer';

import {
  actionCreators as pipelineActionCreators,
  reducer as pipelineReducer,
} from './pipelineReducer';

const actionCreators = {
  ...elementActionCreators,
  ...pipelineActionCreators,
};

const reducer = combineReducers({
  elements: elementReducer,
  pipelines: pipelineReducer,
});

export { actionCreators, reducer };
