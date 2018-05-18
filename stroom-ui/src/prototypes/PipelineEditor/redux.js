import { createAction, handleActions, combineActions } from 'redux-actions';

import { getPipelineAsTree } from './pipelineUtils';

const pipelineChanged = createAction('PIPELINE_CHANGED',
    (pipelineId, pipeline) => ({pipelineId, pipeline}));

// pipelines, keyed on ID, there may be several expressions on a page
const defaultPipelineState = {};

const pipelineReducer = handleActions({
    [pipelineChanged]:
    (state, action) => ({
        ...state,
        [action.payload.pipelineId] : {
            pipeline : action.payload.pipeline,
            asTree : getPipelineAsTree(action.payload.pipeline)
        }
    }),
}, defaultPipelineState);

export {
    pipelineChanged,
    pipelineReducer
}