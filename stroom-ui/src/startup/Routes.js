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
import { AppChrome } from 'sections/AppChrome';
import PipelineSearch from 'components/PipelineSearch';
import XsltEditor from 'prototypes/XsltEditor';
import { HandleAuthenticationResponse } from 'startup/Authentication';
import PipelineEditor from 'components/PipelineEditor';
import DocExplorer from 'components/DocExplorer';
import DataViewer from 'components/DataViewer';
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

      {/* AppChrome paths -- these paths load the relevent sections. */}
      <PrivateRoute
        exact
        path="/s/welcome"
        render={props => (
          <AppChrome {...props} title="Welcome" icon="home">
            <Welcome />
          </AppChrome>
        )}
      />
      <PrivateRoute
        exact
        path="/s/docExplorer"
        render={props => (
          <AppChrome {...props} title="Explorer" icon="eye">
            <DocExplorer explorerId="app-chrome" />
          </AppChrome>
        )}
      />
      <PrivateRoute
        exact
        path="/s/data"
        render={props => (
          <AppChrome {...props} title="Data" icon="database">
            <DataViewer />
          </AppChrome>
        )}
      />
      <PrivateRoute
        exact
        path="/s/pipelines"
        render={props => (
          <AppChrome {...props} title="Pipelines" icon="tasks">
            <PipelineSearch />
          </AppChrome>
        )}
      />

      <PrivateRoute
        exact
        path="/s/pipelines/:pipelineId"
        render={props => (
          <AppChrome {...props} title="Pipelines" icon="tasks">
            <PipelineEditor pipelineId={props.pipelineId} />
          </AppChrome>
        )}
      />

      <PrivateRoute
        exact
        path="/s/processing"
        render={props => (
          <AppChrome {...props} title="Processing" icon="play">
            <TrackerDashboard />
          </AppChrome>
        )}
      />
      <PrivateRoute
        exact
        path="/s/me"
        render={props => (
          <AppChrome {...props} title="Me" icon="user">
            <UserSettings />
          </AppChrome>
        )}
      />
      <PrivateRoute
        exact
        path="/s/users"
        render={props => (
          <AppChrome {...props} title="Users" icon="users">
            <IFrame key="users" url={authUsersUiUrl} />
          </AppChrome>
        )}
      />
      <PrivateRoute
        exact
        path="/s/apikeys"
        render={props => (
          <AppChrome {...props} title="API Keys" icon="key">
            <IFrame key="apikeys" url={authTokensUiUrl} />
          </AppChrome>
        )}
      />

      {/* Direct paths -- these paths make sections accessible outside the AppChrome
        i.e. for when we want to embed them in Stroom. */}
      <PrivateRoute exact path="/trackers" referrer="/trackers" component={TrackerDashboard} />
      <PrivateRoute exact path="/docExplorer" referrer="/docExplorer" component={DocExplorer} />

      {/* TODO: There are no AppChrome routes for the following because the do not have
        TabTypes. Content must to be anchored to something on the sidebar. Otherwise it's
        disconnected from the obvious flow of the app and the mental model of the flow
        the user used to get to the data is broken. Bad. So we could either add an XSLT
        and pipeline sections or we could map them to something deeper, e.g.
           /pipelines/<pipelienId>/xslt/<xsltId>
        Obviously this needs more thinking about. */}
      <PrivateRoute exact path="/XSLT/:xsltId" render={props => <XsltEditor {...props} />} />
      <PrivateRoute
        exact
        path="/Pipeline/:pipelineId"
        render={props => <PipelineEditor {...props} />}
      />

      <PrivateRoute
        exact
        path="/s/doc/XSLT/:xsltId"
        render={props => (
          <AppChrome {...props} title="Edit XSLT" icon="file">
            <XsltEditor xsltId={props.xsltId} />
          </AppChrome>
        )}
      />
      <PrivateRoute
        exact
        path="/s/doc/Pipeline/:pipelineId"
        render={props => (
          <AppChrome {...props} title="Edit Pipeline" icon="file">
            <PipelineEditor pipelineId={props.pipelineId} />
          </AppChrome>
        )}
      />

      {/* Catch all doc route */}
      <PrivateRoute
        exact
        path="/s/doc/:type/:uuid"
        render={props => (
          <AppChrome {...props} title={`Edit ${props.type}`} icon="file">
            <PathNotFound message="no editor provided for this doc ref type " />
          </AppChrome>
        )}
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
