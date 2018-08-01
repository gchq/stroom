/*
 * Copyright 2017 Crown Copyright
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
import { bindActionCreators } from 'redux';
import { connect } from 'react-redux';

import { Grid } from 'semantic-ui-react';

import './ErrorPage.css';

class ErrorPage extends Component {
  render() {
    const { errorMessage, stackTrace, httpErrorCode } = this.props;
    return (
      <Grid className="ErrorPage-card">
        <Grid.Row>
          <h3>There has been an error!</h3>
        </Grid.Row>

        {errorMessage ? (
          <Grid.Row>
            <Grid.Column width={3}>
              <strong>Error message: </strong>
            </Grid.Column>
            <Grid.Column width={13}>
              <code>{errorMessage}</code>
            </Grid.Column>
          </Grid.Row>
        ) : (
          undefined
        )}

        {httpErrorCode ? (
          <Grid.Row>
            <Grid.Column width={3}>
              <strong>HTTP error code:</strong>
            </Grid.Column>
            <Grid.Column width={13}>
              <code>{httpErrorCode}</code>
            </Grid.Column>
          </Grid.Row>
        ) : (
          undefined
        )}

        {stackTrace ? (
          <Grid.Row>
            <Grid.Column width={3}>
              <strong>Stack trace:</strong>
            </Grid.Column>
            <Grid.Column width={13}>
              <code>{stackTrace}</code>
            </Grid.Column>
          </Grid.Row>
        ) : (
          undefined
        )}
      </Grid>
    );
  }
}

const mapStateToProps = state => ({
  errorMessage: state.errorPage.errorMessage,
  stackTrace: state.errorPage.stackTrace,
  httpErrorCode: state.errorPage.httpErrorCode,
});

const mapDispatchToProps = dispatch => bindActionCreators({}, dispatch);

export default connect(mapStateToProps, mapDispatchToProps)(ErrorPage);
