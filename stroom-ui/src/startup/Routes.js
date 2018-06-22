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
import { compose, lifecycle, withProps } from 'recompose';
import { connect } from 'react-redux';

import ErrorPage from 'sections/ErrorPage';
import OriginalList from 'prototypes/OriginalList';
import Graph from 'prototypes/Graph';
import TrackerDashboard from 'sections/TrackerDashboard';
import AppChrome from 'sections/AppChrome';
import { AuthenticationRequest, HandleAuthenticationResponse } from 'startup/Authentication';
import { PipelineEditor } from 'prototypes/PipelineEditor';
import DocExplorer from 'components/DocExplorer';

import PathNotFound from 'sections/PathNotFound';

import { actionCreators as configActionCreators, withConfigReady } from './config';

const { updateConfig } = configActionCreators;

const enhance = compose(
  connect((state, props) => ({}), {
    updateConfig,
  }),
  lifecycle({
    componentDidMount() {
      fetch('/config.json', { method: 'get' })
        .then(response => response.json())
        .then(config => this.props.updateConfig(config));
    },
  }),
  withConfigReady,
  withRouter,
  connect(
    state => ({
      idToken: state.authentication.idToken,
      // showUnauthorizedDialog: state.login.showUnauthorizedDialog,
      advertisedUrl: state.config.advertisedUrl,
      appClientId: state.config.appClientId,
      authenticationServiceUrl: state.config.authenticationServiceUrl,
      authorisationServiceUrl: state.config.authorisationServiceUrl,
    }),
    {},
  ),
  withProps(({idToken}) => ({
    isLoggedIn: !!idToken
  }))
);

const Routes = enhance(({
  isLoggedIn,
  appClientId,
  history,
  authenticationServiceUrl,
  authorisationServiceUrl,
  advertisedUrl,
}) => (
  <Router history={history} basename="/">
    <Switch>
      {/* Authentication routes */}
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

      {/* Application routes - no authentication required */}
      <Route exact path="/error" component={ErrorPage} />
      <Route exact path="/prototypes/original_list" component={OriginalList} />
      <Route exact path="/prototypes/graph" component={Graph} />

      {/* Application Routes - require authentication */}
      <Route
        exact
        path="/"
        render={() =>
          (isLoggedIn ? (
            <AppChrome />
          ) : (
            <AuthenticationRequest
              referrer="/"
              uiUrl={advertisedUrl}
              appClientId={appClientId}
              authenticationServiceUrl={authenticationServiceUrl}
            />
          ))
        }
      />

      <Route
        exact
        path="/trackers"
        render={() =>
          (isLoggedIn ? (
            <TrackerDashboard />
          ) : (
            <AuthenticationRequest
              referrer="/trackers"
              uiUrl={advertisedUrl}
              appClientId={appClientId}
              authenticationServiceUrl={authenticationServiceUrl}
              appPermission="MANAGE_USERS"
            />
          ))
        }
      />

      <Route
        exact
        path="/pipelines/:pipelineId"
        render={({ match }) =>
          (isLoggedIn ? (
            <PipelineEditor
              pipelineId={match.params.pipelineId}
            />
          ) : (
            <AuthenticationRequest
              referrer={match.url}
              uiUrl={advertisedUrl}
              appClientId={appClientId}
              authenticationServiceUrl={authenticationServiceUrl}
            />
          ))
        }
      />

      <Route
        exact
        path="/explorerTree"
        render={({ match }) =>
          (isLoggedIn ? (
            <DocExplorer explorerId="singleton" />
          ) : (
            <AuthenticationRequest
              referrer={match.url}
              uiUrl={advertisedUrl}
              appClientId={appClientId}
              authenticationServiceUrl={authenticationServiceUrl}
            />
          ))
        }
      />

      <Route component={PathNotFound} />
    </Switch>
  </Router>
));

Routes.contextTypes = {
  store: PropTypes.object,
  router: PropTypes.shape({
    history: object.isRequired,
  }),
};

export default Routes;
