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
import { AppChrome } from 'sections/AppChrome';
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

      {/* AppChrome paths -- these paths load the relevent sections. */}
      <PrivateRoute
        exact
        path="/"
        render={props => (
          <AppChrome
            activeMenuItem="Welcome"
            {...props}
            headerContent={<Header.Content>Welcome</Header.Content>}
            icon="home"
            content={<Welcome />}
          />
        )}
      />
      <PrivateRoute
        exact
        path="/s/welcome"
        render={props => (
          <AppChrome
            activeMenuItem="Welcome"
            {...props}
            headerContent={<Header.Content>Welcome</Header.Content>}
            icon="home"
            content={<Welcome />}
          />
        )}
      />
      <PrivateRoute
        exact
        path="/s/docExplorer"
        render={props => (
          <AppChrome
            activeMenuItem="Explorer"
            {...props}
            headerContent={<Header.Content>Explorer</Header.Content>}
            icon="eye"
            content={<DocExplorer explorerId="app-chrome" />}
            actionBarAdditionalItems={<DocExplorerActionBarItems explorerId="app-chrome" />}
          />
        )}
      />
      <PrivateRoute
        exact
        path="/s/data"
        render={props => (
          <AppChrome
            activeMenuItem="Data"
            {...props}
            headerContent={<Header.Content>Data</Header.Content>}
            icon="database"
            content={<DataViewer dataViewerId="system" />}
            actionBarAdditionalItems={<DataViewerActionBarItems dataViewerId="system" />}
          />
        )}
      />
      <PrivateRoute
        exact
        path="/s/pipelines"
        render={props => (
          <AppChrome
            activeMenuItem="Pipelines"
            {...props}
            headerContent={<Header.Content>Pipelines</Header.Content>}
            icon="tasks"
            content={<PipelineSearch />}
          />
        )}
      />

      <PrivateRoute
        exact
        path="/s/processing"
        render={props => (
          <AppChrome
            activeMenuItem="Processing"
            {...props}
            headerContent={<Header.Content>Processing</Header.Content>}
            icon="play"
            content={<TrackerDashboard />}
          />
        )}
      />
      <PrivateRoute
        exact
        path="/s/me"
        render={props => (
          <AppChrome
            activeMenuItem="Me"
            {...props}
            headerContent={<Header.Content>Me</Header.Content>}
            icon="user"
            content={<UserSettings />}
          />
        )}
      />
      <PrivateRoute
        exact
        path="/s/users"
        render={props => (
          <AppChrome
            activeMenuItem="Users"
            {...props}
            headerContent={<Header.Content>Users</Header.Content>}
            icon="users"
            content={<IFrame key="users" url={authUsersUiUrl} />}
          />
        )}
      />
      <PrivateRoute
        exact
        path="/s/apikeys"
        render={props => (
          <AppChrome
            activeMenuItem="API Keys"
            {...props}
            headerContent={<Header.Content>API Keys</Header.Content>}
            icon="key"
            content={<IFrame key="apikeys" url={authTokensUiUrl} />}
          />
        )}
      />

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
          <AppChrome
            activeMenuItem="Explorer"
            {...props}
            headerContent={<Header.Content>Edit XSLT</Header.Content>}
            icon="file"
            content={<XsltEditor xsltId={props.xsltId} />}
          />
        )}
      />
      <PrivateRoute
        exact
        path="/s/doc/Pipeline/:pipelineId"
        render={props => (
          <AppChrome
            activeMenuItem="Pipelines"
            {...props}
            headerContent={
              <PipelineEditorHeaderContent pipelineId={props.match.params.pipelineId} />
            }
            icon="tasks"
            content={<PipelineEditor pipelineId={props.pipelineId} />}
            actionBarAdditionalItems={
              <PipelineEditorActionBarItems pipelineId={props.pipelineId} />
            }
          />
        )}
      />

      {/* Catch all doc route */}
      <PrivateRoute
        exact
        path="/s/doc/:type/:uuid"
        render={props => (
          <AppChrome
            activeMenuItem="Explorer"
            {...props}
            headerContent={<Header.Content>{`Edit ${props.type}`}</Header.Content>}
            icon="file"
            content={<PathNotFound message="no editor provided for this doc ref type " />}
          />
        )}
      />

      {/* Default route */}
      <Route
        render={() => (
          <AppChrome
            activeMenuItem="Welcome"
            headerContent={<Header.Content>Not Found</Header.Content>}
            icon="exclamation triangle"
            content={<PathNotFound />}
          />
        )}
      />
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
