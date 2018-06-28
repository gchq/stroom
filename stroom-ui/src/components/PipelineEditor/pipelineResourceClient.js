import { actionCreators } from './redux';
import { wrappedGet } from 'lib/fetchTracker.redux';

const { pipelineReceived, pipelineSaved } = actionCreators;

export const fetchPipeline = pipelineId => (dispatch, getState) => {
  const state = getState();
  const url = `${state.config.pipelineServiceUrl}/${pipelineId}`;
  wrappedGet(dispatch, state, url, pipeline => dispatch(pipelineReceived(pipelineId, pipeline)));
};

export const savePipeline = pipelineId => (dispatch, getState) => {
  const state = getState();
  const url = `${state.config.pipelineServiceUrl}/${pipelineId}`;

  const pipelineData = state.pipelines[pipelineId].pipeline;
  const body = pipelineData.configStack[pipelineData.configStack.length - 1];

  wrappedPost(dispatch, state, url, body, pipelineId => dispatch(pipelineSaved(pipelineId)));
};
