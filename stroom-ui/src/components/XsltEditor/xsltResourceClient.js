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

const { xsltReceived, xsltSaved } = actionCreators;

export const fetchXslt = xsltId => (dispatch, getState) => {
  const state = getState();
  const url = `${state.config.xsltServiceUrl}/${xsltId}`;
  wrappedGet(
    dispatch,
    state,
    url,
    response => response.text().then(xslt => dispatch(xsltReceived(xsltId, xslt))),
    {
      headers: {
        Accept: 'application/xml',
        'Content-Type': 'application/xml',
      },
    },
  );
};

export const saveXslt = xsltId => (dispatch, getState) => {
  const state = getState();
  const url = `${state.config.xsltServiceUrl}/${xsltId}`;

  const body = state.xslt[xsltId].xsltData;

  wrappedPost(
    dispatch,
    state,
    url,
    response => response.text().then(response => dispatch(xsltSaved(xsltId))),
    {
      body,
      headers: {
        Accept: 'application/xml',
        'Content-Type': 'application/xml',
      },
    },
  );
};
