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
import { Dispatch } from "redux";
import { actionCreators } from "./redux";
import { wrappedGet, wrappedPost } from "../../lib/fetchTracker.redux";
import { GlobalStoreState } from "../../startup/reducers";

const { xsltReceived, xsltSaved } = actionCreators;

export const fetchXslt = (xsltUuid: string) => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  const state = getState();
  const url = `${state.config.values.stroomBaseServiceUrl}/xslt/v1/${xsltUuid}`;
  wrappedGet(
    dispatch,
    state,
    url,
    response =>
      response
        .text()
        .then((xslt: string) => dispatch(xsltReceived(xsltUuid, xslt))),
    {
      headers: {
        Accept: "application/xml",
        "Content-Type": "application/xml"
      }
    }
  );
};

export const saveXslt = (xsltUuid: string) => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  const state = getState();
  const url = `${state.config.values.stroomBaseServiceUrl}/xslt/v1/${xsltUuid}`;

  const body = state.xsltEditor[xsltUuid].xsltData;

  wrappedPost(
    dispatch,
    state,
    url,
    response => response.text().then(() => dispatch(xsltSaved(xsltUuid))),
    {
      body,
      headers: {
        Accept: "application/xml",
        "Content-Type": "application/xml"
      }
    }
  );
};
