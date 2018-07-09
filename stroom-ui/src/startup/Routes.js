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

import ErrorPage from 'sections/ErrorPage';
import OriginalList from 'prototypes/OriginalList';
import Graph from 'prototypes/Graph';
import TrackerDashboard from 'sections/TrackerDashboard';
import AppChrome from 'sections/AppChrome';
import XsltEditor from 'prototypes/XsltEditor';
import { AuthenticationRequest, HandleAuthenticationResponse } from 'startup/Authentication';
import PipelineEditor from 'components/PipelineEditor';
import DocExplorer from 'components/DocExplorer';

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
      <Route exact path="/prototypes/original_list" component={OriginalList} />
      <Route exact path="/prototypes/graph" component={Graph} />

      <PrivateRoute exact path="/" referrer="/" component={AppChrome} />
      <PrivateRoute exact path="/trackers" referrer="/trackers" component={TrackerDashboard} />
      <PrivateRoute
        exact
        path="/pipelines/:pipelineId"
        referrer="/pipelines"
        component={PipelineEditor}
      />
      <PrivateRoute exact path="/xslt/:xsltId" referrer="/xslt" component={XsltEditor} />
      <PrivateRoute exact path="/explorerTree" referrer="/explorerTree" component={DocExplorer} />

      <Route component={PathNotFound} />
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
