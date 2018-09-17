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
import { wrappedGet, wrappedPost } from '../../lib/fetchTracker.redux';

const { xsltReceived, xsltSaved } = actionCreators;

export const fetchXslt = xsltUuid => (dispatch, getState) => {
  const state = getState();
  const url = `${state.config.stroomBaseServiceUrl}/xslt/v1/${xsltUuid}`;
  wrappedGet(
    dispatch,
    state,
    url,
    response => response.text().then(xslt => dispatch(xsltReceived(xsltUuid, xslt))),
    {
      headers: {
        Accept: 'application/xml',
        'Content-Type': 'application/xml',
      },
    },
  );
};

export const saveXslt = xsltUuid => (dispatch, getState) => {
  const state = getState();
  const url = `${state.config.stroomBaseServiceUrl}/xslt/v1/${xsltUuid}`;

  const body = state.xslt[xsltUuid].xsltData;

  wrappedPost(
    dispatch,
    state,
    url,
    response => response.text().then(response => dispatch(xsltSaved(xsltUuid))),
    {
      body,
      headers: {
        Accept: 'application/xml',
        'Content-Type': 'application/xml',
      },
    },
  );
};
