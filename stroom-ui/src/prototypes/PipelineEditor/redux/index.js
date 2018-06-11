import { actionCreators as elementActionCreators, elementReducer } from './elementReducer';

import { actionCreators as pipelineActionCreators, pipelineReducer } from './pipelineReducer';

const actionCreators = {
  ...elementActionCreators,
  ...pipelineActionCreators,
};

export { actionCreators, elementReducer, pipelineReducer };
