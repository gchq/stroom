import { actionCreators as elementActionCreators, reducer as elementReducer } from './elementReducer';

import { actionCreators as pipelineActionCreators, reducer as pipelineReducer } from './pipelineReducer';

const actionCreators = {
  ...elementActionCreators,
  ...pipelineActionCreators,
};

export { actionCreators, elementReducer, pipelineReducer };
