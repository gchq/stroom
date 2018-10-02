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
import {
  Route,
  Router,
  Switch,
  withRouter,
  RouteComponentProps
} from "react-router-dom";
import { compose, withProps } from "recompose";
import { connect } from "react-redux";

// TODO
import ErrorPage from "../components/ErrorPage";
import { appChromeRoutes } from "../sections/AppChrome";
// import { Processing } from "../sections/Processing";
import { HandleAuthenticationResponse } from "./Authentication";
import { GlobalStoreState } from "./reducers";

import withConfig from "./withConfig";

import { PrivateRoute } from "./Authentication";
import PathNotFound from "../components/PathNotFound";

export interface Props {}

export interface EnhancedProps extends Props, RouteComponentProps {
  authenticationServiceUrl: string;
  authorisationServiceUrl: string;
  authUsersUiUrl: string;
  authTokensUiUrl: string;
}

const enhance = compose<EnhancedProps, Props>(
  withConfig,
  withRouter,
  connect<{}, {}, {}, GlobalStoreState>(
    ({ authentication: { idToken } }) => ({
      idToken
      // showUnauthorizedDialog: state.login.showUnauthorizedDialog,
    }),
    {}
  ),
  withProps(
    ({ config: { authenticationServiceUrl, authorisationServiceUrl } }) => ({
      authenticationServiceUrl,
      authorisationServiceUrl
    })
  )
);

const Routes = ({
  history,
  authenticationServiceUrl,
  authorisationServiceUrl
}: EnhancedProps) => (
  <Router history={history}>
    {/* basename="/"> TODO */}
    <Switch>
      <Route
        exact
        path="/handleAuthenticationResponse"
        render={() => (
          <HandleAuthenticationResponse
            authenticationServiceUrl={authenticationServiceUrl}
            authorisationServiceUrl={authorisationServiceUrl}
          />
        )}
      />

      <Route exact path="/error" component={ErrorPage} />

      {appChromeRoutes.map((p, i) => (
        <PrivateRoute key={i} {...p} />
      ))}

      {/* TODO Direct paths -- these paths make sections accessible outside the AppChrome
        i.e. for when we want to embed them in Stroom. */}
      {/* <PrivateRoute
        exact
        path="/trackers"
        referrer="/trackers"
        render={() => <Processing />}
      /> */}

      {/* Default route */}
      <Route render={() => <PathNotFound message="Invalid path" />} />
    </Switch>
  </Router>
);

// Routes.contextTypes = {
//   store: PropTypes.object,
//   router: PropTypes.shape({
//     history: object.isRequired,
//   }),
// };

export default enhance(Routes);
