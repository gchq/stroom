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
// import ErrorPage from "../components/ErrorPage";
// import { appChromeRoutes } from "../sections/AppChrome";
// import { Processing } from "../sections/Processing";
import { HandleAuthenticationResponse } from "./Authentication";
import { GlobalStoreState } from "./reducers";

import withConfig from "./withConfig";

// import { PrivateRoute } from "./Authentication";

export interface Props {}

export interface EnhancedProps extends Props, RouteComponentProps {
  isLoggedIn: boolean;
  appClientId: string;
  authenticationServiceUrl: string;
  authorisationServiceUrl: string;
  advertisedUrl: string;
  authUsersUiUrl: string;
  authTokensUiUrl: string;
}

const enhance = compose<EnhancedProps, Props>(
  withConfig,
  withRouter,
  connect(
    ({ authentication: { idToken } }: GlobalStoreState) => ({
      idToken
      // showUnauthorizedDialog: state.login.showUnauthorizedDialog,
    }),
    {}
  ),
  withProps(
    ({
      idToken,
      config: {
        advertisedUrl,
        appClientId,
        authenticationServiceUrl,
        authorisationServiceUrl
      }
    }) => ({
      isLoggedIn: !!idToken,
      advertisedUrl,
      appClientId,
      authenticationServiceUrl,
      authorisationServiceUrl
    })
  )
);

const Routes = ({
  isLoggedIn,
  appClientId,
  history,
  authenticationServiceUrl,
  authorisationServiceUrl,
  advertisedUrl,
  authUsersUiUrl,
  authTokensUiUrl
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

      {/* TODO */}
      {/* <Route exact path="/error" component={ErrorPage} /> */}

      {/* TODO */}
      {/* {appChromeRoutes.map((p, i) => (
        <PrivateRoute key={i} {...p} />
      ))} */}

      {/* TODO Direct paths -- these paths make sections accessible outside the AppChrome
        i.e. for when we want to embed them in Stroom. */}
      {/* <PrivateRoute
        exact
        path="/trackers"
        referrer="/trackers"
        render={() => <Processing />}
      /> */}

      {/* Default route TODO */}
      {/* <Route render={appChromeRoutes.notFound} /> */}
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
