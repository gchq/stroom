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
import TrackerDashboard from 'sections/TrackerDashboard';
import { TabTypeDisplayInfo, AppChrome } from 'sections/AppChrome';
import XsltEditor from 'prototypes/XsltEditor';
import { HandleAuthenticationResponse } from 'startup/Authentication';
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

      {/* AppChrome paths -- these paths load the relevent sections. */}
      <PrivateRoute exact path="/" referrer="/" component={AppChrome} />
      {Object.values(TabTypeDisplayInfo)
        .map(t => t.path)
        .map(path => (
          <PrivateRoute key={path} exact path={path} referrer={path} component={AppChrome} />
        ))}

      {/* Direct paths -- these paths make sections accessible outside the AppChrome
        i.e. for when we want to embed them in Stroom. */}
      <PrivateRoute exact path="/trackers" referrer="/trackers" component={TrackerDashboard} />
      {/* TODO: What path do we want for docExplorer? */}
      <PrivateRoute exact path="/explorerTree" referrer="/explorerTree" component={DocExplorer} />
      <PrivateRoute exact path="/docExplorer" referrer="/docExplorer" component={DocExplorer} />

      {/* TODO: There are no AppChrome routes for the following because the do not have
        TabTypes. Content must to be anchored to something on the sidebar. Otherwise it's
        disconnected from the obvious flow of the app and the mental model of the flow
        the user used to get to the data is broken. Bad. So we could either add an XSLT
        and pipeline sections or we could map them to something deeper, e.g.
           /pipelines/<pipelienId>/xslt/<xsltId>
        Obviously this needs more thinking about. */}
      <PrivateRoute exact path="/xslt/:xsltId" referrer="/xslt" component={XsltEditor} />
      <PrivateRoute
        exact
        path="/pipelines/:pipelineId"
        referrer="/pipelines"
        component={PipelineEditor}
      />

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
