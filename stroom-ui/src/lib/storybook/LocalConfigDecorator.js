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
import { connect } from 'react-redux';
import { compose, lifecycle } from 'recompose';

import { actionCreators as configActionCreators } from 'startup/config';

import IP_ADDRESS from './IpAddress';

const { updateConfig } = configActionCreators;

const enhance = compose(
  connect((state, props) => ({}), {
    updateConfig,
  }),
  lifecycle({
    componentDidMount() {
      this.props.updateConfig({
        authenticationServiceUrl: `https://${IP_ADDRESS}/authService/authentication/v1`,
        authorisationServiceUrl: `http://${IP_ADDRESS}:8080/api/authorisation/v1`,
        streamTaskServiceUrl: `http://${IP_ADDRESS}:8080/api/streamtasks/v1`,
        pipelineServiceUrl: `http://${IP_ADDRESS}:8080/api/pipelines/v1`,
        explorerServiceUrl: `http://${IP_ADDRESS}:8080/api/explorer/v1`,
        elementServiceUrl: `http://${IP_ADDRESS}:8080/api/elements/v1`,
        advertisedUrl: `http://${IP_ADDRESS}:5001`,
        appClientId: 'stroom-ui',
      });
    },
  }),
);

const LocalConfigComponent = enhance(({ children }) => <div>{children}</div>);

export const LocalConfigDecorator = storyFn => (
  <LocalConfigComponent>{storyFn()}</LocalConfigComponent>
);
