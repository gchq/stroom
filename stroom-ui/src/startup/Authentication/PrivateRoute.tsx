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

import * as React from "react";
import { Route } from "react-router-dom";
import { compose, withProps } from "recompose";
import { connect } from "react-redux";
import { GlobalStoreState } from "../../startup/reducers";

import AuthenticationRequest from "./AuthenticationRequest";

export interface Props {
  isLoggedIn: boolean;
  render: (p: any) => any;
}

export interface ConnectState {
  idToken?: string;
  // showUnauthorizedDialog: state.login.showUnauthorizedDialog,
  advertisedUrl?: string;
  appClientId?: string;
  authenticationServiceUrl?: string;
  authorisationServiceUrl?: string;
}
export interface ConnectDispatch {}

export interface EnhancedProps extends Props {
  advertisedUrl: string;
  appClientId: string;
  authenticationServiceUrl: string;
}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({
      authentication: { idToken },
      config: {
        values: {
          advertisedUrl,
          appClientId,
          authenticationServiceUrl,
          authorisationServiceUrl
        }
      }
    }) => ({
      idToken,
      // showUnauthorizedDialog: state.login.showUnauthorizedDialog,
      advertisedUrl,
      appClientId,
      authenticationServiceUrl,
      authorisationServiceUrl
    }),
    {}
  ),
  withProps(({ idToken }) => ({
    isLoggedIn: !!idToken
  }))
);

const PrivateRoute = ({
  isLoggedIn,
  advertisedUrl,
  appClientId,
  authenticationServiceUrl,
  render,
  ...rest
}: EnhancedProps) => (
  <Route
    {...rest}
    render={props =>
      isLoggedIn ? (
        render({ ...props })
      ) : (
        <AuthenticationRequest
          referrer={props.match.url}
          uiUrl={advertisedUrl}
          appClientId={appClientId}
          authenticationServiceUrl={authenticationServiceUrl}
        />
      )
    }
  />
);

export default enhance(PrivateRoute);
