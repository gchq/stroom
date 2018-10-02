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
import { actionCreators } from "./redux";
import { wrappedGet, wrappedPost } from "../../lib/fetchTracker.redux";
import { PipelineModelType, PipelineSearchResultType } from "../../types";
import { Dispatch } from "redux";
import { GlobalStoreState } from "../../startup/reducers";

const {
  pipelineReceived,
  pipelineSaveRequested,
  pipelineSaved,
  pipelinesReceived
} = actionCreators;

export const fetchPipeline = (pipelineId: string) => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  const state = getState();
  const url = `${
    state.config.values.stroomBaseServiceUrl
  }/pipelines/v1/${pipelineId}`;
  wrappedGet(dispatch, state, url, response =>
    response
      .json()
      .then((pipeline: PipelineModelType) =>
        dispatch(pipelineReceived(pipelineId, pipeline))
      )
  );
};

export const savePipeline = (pipelineId: string) => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  const state = getState();
  const url = `${
    state.config.values.stroomBaseServiceUrl
  }/pipelines/v1/${pipelineId}`;

  const { pipeline } = state.pipelineEditor.pipelineStates[pipelineId];
  const body = JSON.stringify(pipeline);

  dispatch(pipelineSaveRequested(pipelineId));

  wrappedPost(
    dispatch,
    state,
    url,
    response => response.text().then(() => dispatch(pipelineSaved(pipelineId))),
    {
      body
    }
  );
};

export const searchPipelines = () => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  const state = getState();
  let url = `${state.config.values.stroomBaseServiceUrl}/pipelines/v1/?`;
  const { filter, pageSize, pageOffset } = state.pipelineEditor.search.criteria;

  if (filter !== undefined && filter !== "") {
    url += `&filter=${filter}`;
  }

  if (pageSize !== undefined && pageOffset !== undefined) {
    url += `&pageSize=${pageSize}&offset=${pageOffset}`;
  }

  const forceGet = true;
  wrappedGet(
    dispatch,
    state,
    url,
    response =>
      response
        .json()
        .then((response: PipelineSearchResultType) =>
          dispatch(pipelinesReceived(response))
        ),
    {},
    forceGet
  );
};
