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

import React from 'react';
import { connect } from 'react-redux';
import { Grid, Header, Icon } from 'semantic-ui-react';

const enhance = connect(
  ({ errorPage: { errorMessage, stackTrace, httpErrorCode } }) => ({
    errorMessage,
    stackTrace,
    httpErrorCode,
  }),
  {},
);

const ErrorSection = ({ title, errorData }) => (
  <Grid.Row>
    <Grid.Column width={3}>
      <strong>{title}: </strong>
    </Grid.Column>
    <Grid.Column width={13}>
      <code>{errorData}</code>
    </Grid.Column>
  </Grid.Row>
);

const ErrorPage = ({ errorMessage, stackTrace, httpErrorCode }) => (
  <React.Fragment>
    <Grid className="content-tabs__grid">
      <Grid.Row>
        <Grid.Column width={12}>
          <Header as="h3">
            <Icon name="exclamation circle" color="red" />
            There has been an error!
          </Header>
        </Grid.Column>
      </Grid.Row>
    </Grid>

    <Grid className="error-page__details">
      {errorMessage && <ErrorSection errorData={errorMessage} title="Error Message" />}
      {httpErrorCode !== 0 &&
        httpErrorCode && <ErrorSection errorData={httpErrorCode} title="HTTP error code" />}
      {stackTrace && <ErrorSection errorData={stackTrace} title="Stack trace" />}
    </Grid>
  </React.Fragment>
);

export default enhance(ErrorPage);
