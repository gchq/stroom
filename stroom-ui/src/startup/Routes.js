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

import React, { Component } from 'react';
import PropTypes, { object } from 'prop-types';
import { Route, Router, Switch, withRouter } from 'react-router-dom';
import { bindActionCreators } from 'redux';
import { connect } from 'react-redux';

import ErrorPage from 'sections/ErrorPage';
import OriginalList from 'prototypes/OriginalList';
import Graph from 'prototypes/Graph';
import TrackerDashboard from 'sections/TrackerDashboard';
import AppChrome from 'sections/AppChrome';
import { AuthenticationRequest, HandleAuthenticationResponse } from 'startup/Authentication';
import { PipelineEditor } from 'prototypes/PipelineEditor';
import { DocExplorer } from 'components/DocExplorer';

import PathNotFound from 'sections/PathNotFound';

class Routes extends Component {
  isLoggedIn() {
    return !!this.props.idToken;
  }

  render() {
    const { history } = this.props;
    return (
      <Router history={history} basename="/">
        <Switch>
          {/* Authentication routes */}
          <Route
            exact
            path="/handleAuthenticationResponse"
            render={() => (
              <HandleAuthenticationResponse
                authenticationServiceUrl={this.props.authenticationServiceUrl}
                authorisationServiceUrl={this.props.authorisationServiceUrl}
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
              (this.isLoggedIn() ? (
                <AppChrome />
              ) : (
                <AuthenticationRequest
                  referrer="/"
                  uiUrl={this.props.advertisedUrl}
                  appClientId={this.props.appClientId}
                  authenticationServiceUrl={this.props.authenticationServiceUrl}
                />
              ))
            }
          />

          <Route
            exact
            path="/trackers"
            render={() =>
              (this.isLoggedIn() ? (
                <TrackerDashboard />
              ) : (
                <AuthenticationRequest
                  referrer="/trackers"
                  uiUrl={this.props.advertisedUrl}
                  appClientId={this.props.appClientId}
                  authenticationServiceUrl={this.props.authenticationServiceUrl}
                  appPermission="MANAGE_USERS"
                />
              ))
            }
          />

          <Route
            exact
            path="/pipelines/:pipelineId"
            render={({ match }) =>
              (this.isLoggedIn() ? (
                <PipelineEditor
                  shouldFetchElementsFromServer
                  shouldFetchPipelineFromServer
                  pipelineId={match.params.pipelineId}
                />
              ) : (
                <AuthenticationRequest
                  referrer={match.url}
                  uiUrl={this.props.advertisedUrl}
                  appClientId={this.props.appClientId}
                  authenticationServiceUrl={this.props.authenticationServiceUrl}
                />
              ))
            }
          />

          <Route
            exact
            path="/explorerTree"
            render={({ match }) =>
              (this.isLoggedIn() ? (
                <DocExplorer shouldFetchTreeFromServer explorerId="singleton" />
              ) : (
                <AuthenticationRequest
                  referrer={match.url}
                  uiUrl={this.props.advertisedUrl}
                  appClientId={this.props.appClientId}
                  authenticationServiceUrl={this.props.authenticationServiceUrl}
                />
              ))
            }
          />

          <Route component={PathNotFound} />
        </Switch>
      </Router>
    );
  }
}

Routes.contextTypes = {
  store: PropTypes.object,
  router: PropTypes.shape({
    history: object.isRequired,
  }),
};

Routes.propTypes = {
  idToken: PropTypes.string.isRequired,
  // showUnauthorizedDialog: PropTypes.bool.isRequired
};

const mapStateToProps = state => ({
  idToken: state.authentication.idToken,
  // showUnauthorizedDialog: state.login.showUnauthorizedDialog,
  advertisedUrl: state.config.advertisedUrl,
  appClientId: state.config.appClientId,
  authenticationServiceUrl: state.config.authenticationServiceUrl,
  authorisationServiceUrl: state.config.authorisationServiceUrl,
});

const mapDispatchToProps = dispatch =>
  bindActionCreators(
    {
      // handleSessionTimeout
    },
    dispatch,
  );

export default withRouter(connect(mapStateToProps, mapDispatchToProps)(Routes));
