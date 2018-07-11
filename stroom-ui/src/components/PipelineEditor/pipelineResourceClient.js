/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { actionCreators } from './redux';
import { wrappedGet, wrappedPost } from 'lib/fetchTracker.redux';

const { pipelineReceived, pipelineSaveRequested, pipelineSaved } = actionCreators;

export const fetchPipeline = pipelineId => (dispatch, getState) => {
  const state = getState();
  const url = `${state.config.pipelineServiceUrl}/${pipelineId}`;
  wrappedGet(dispatch, state, url, response =>
    response.json().then(pipeline => dispatch(pipelineReceived(pipelineId, pipeline))));
};

export const savePipeline = pipelineId => (dispatch, getState) => {
  const state = getState();
  const url = `${state.config.pipelineServiceUrl}/${pipelineId}`;

  const pipelineData = state.pipelineEditor.pipelines[pipelineId].pipeline;
  const body = JSON.stringify(pipelineData.configStack[pipelineData.configStack.length - 1]);

  dispatch(pipelineSaveRequested(pipelineId));

  wrappedPost(
    dispatch,
    state,
    url,
    response => response.text().then(response => dispatch(pipelineSaved(pipelineId))),
    {
      body,
    },
  );
};
