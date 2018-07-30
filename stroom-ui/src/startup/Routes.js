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

import React from 'react';
import PropTypes, { object } from 'prop-types';
import { Route, Router, Switch, withRouter } from 'react-router-dom';
import { compose, withProps } from 'recompose';
import { connect } from 'react-redux';
import { Header } from 'semantic-ui-react';

import ErrorPage from 'sections/ErrorPage';
import TrackerDashboard from 'sections/TrackerDashboard';
import AppChrome, { appChromeRoutes } from 'sections/AppChrome';
import PipelineSearch from 'components/PipelineSearch';
import XsltEditor from 'prototypes/XsltEditor';
import { HandleAuthenticationResponse } from 'startup/Authentication';
import PipelineEditor, {
  ActionBarItems as PipelineEditorActionBarItems,
  HeaderContent as PipelineEditorHeaderContent,
} from 'components/PipelineEditor';
import DocExplorer, { ActionBarItems as DocExplorerActionBarItems } from 'components/DocExplorer';
import DataViewer, { ActionBarItems as DataViewerActionBarItems } from 'components/DataViewer';
import UserSettings from 'prototypes/UserSettings';
import IFrame from 'components/IFrame';
import Welcome from 'sections/Welcome';

import PathNotFound from 'sections/PathNotFound';

import { withConfig } from './config';

import { PrivateRoute } from './Authentication';

const enhance = compose(
  withConfig,
  withRouter,
  connect(
    state => ({
      idToken: state.authentication.idToken,
      // showUnauthorizedDialog: state.login.showUnauthorizedDialog,
      advertisedUrl: state.config.advertisedUrl,
      appClientId: state.config.appClientId,
      authenticationServiceUrl: state.config.authenticationServiceUrl,
      authorisationServiceUrl: state.config.authorisationServiceUrl,
      authUsersUiUrl: state.config.authUsersUiUrl,
      authTokensUiUrl: state.config.authTokensUiUrl,
    }),
    {},
  ),
  withProps(({ idToken }) => ({
    isLoggedIn: !!idToken,
  })),
);

const Routes = ({
  isLoggedIn,
  appClientId,
  history,
  authenticationServiceUrl,
  authorisationServiceUrl,
  advertisedUrl,
  authUsersUiUrl,
  authTokensUiUrl,
}) => (
  <Router history={history} basename="/">
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

      {appChromeRoutes.map(p => <Route key={p.path} {...p} />)}

      {/* Direct paths -- these paths make sections accessible outside the AppChrome
        i.e. for when we want to embed them in Stroom. */}
      <PrivateRoute
        exact
        path="/trackers"
        referrer="/trackers"
        render={() => <TrackerDashboard />}
      />
      <PrivateRoute
        exact
        path="/docExplorer"
        referrer="/docExplorer"
        render={() => <DocExplorer />}
      />

      {/* Default route */}
      <Route render={appChromeRoutes.notFound} />
    </Switch>
  </Router>
);

Routes.contextTypes = {
  store: PropTypes.object,
  router: PropTypes.shape({
    history: object.isRequired,
  }),
};

export default enhance(Routes);
