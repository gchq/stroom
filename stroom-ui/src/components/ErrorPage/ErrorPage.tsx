/*
 * Copyright 2017 Crown Copyright
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

import * as React from "react";
import { connect } from "react-redux";
import IconHeader from "../IconHeader";
import { StoreState as ErrorStoreState } from "./redux";
import { GlobalStoreState } from "../../startup/reducers";
import ErrorSection from "./ErrorSection";

export interface Props {}
export interface ConnectState extends ErrorStoreState {}
export interface ConnectDispatch {}
export interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

const enhance = connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
  ({ errorPage: { errorMessage, stackTrace, httpErrorCode } }) => ({
    errorMessage,
    stackTrace,
    httpErrorCode
  }),
  {}
);

const ErrorPage = ({
  errorMessage,
  stackTrace,
  httpErrorCode
}: EnhancedProps) => (
  <div>
    <IconHeader icon="exclamation-circle" text="There has been an error!" />

    <div className="ErrorPage__details">
      {errorMessage && (
        <ErrorSection errorData={errorMessage} title="Error Message" />
      )}
      {httpErrorCode !== 0 &&
        httpErrorCode && (
          <ErrorSection errorData={httpErrorCode} title="HTTP error code" />
        )}
      {stackTrace && (
        <ErrorSection errorData={stackTrace} title="Stack trace" />
      )}
    </div>
  </div>
);

export default enhance(ErrorPage);
