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
import { Polly } from '@pollyjs/core';

import { actionCreators as configActionCreators } from 'startup/config';

const { updateConfig, clearConfig } = configActionCreators;

export const DevServerDecorator = storyFn => <DevServerComponent>{storyFn()}</DevServerComponent>;

const testConfig = {
  authenticationServiceUrl: '/authService/authentication/v1',
  authorisationServiceUrl: '/api/authorisation/v1',
  streamTaskServiceUrl: '/api/streamtasks/v1',
  pipelineServiceUrl: '/api/pipelines/v1',
  explorerServiceUrl: '/api/explorer/v1',
  elementServiceUrl: '/api/elements/v1',
  advertisedUrl: '/',
  appClientId: 'stroom-ui',
};

const polly = new Polly('Mock Stroom API');
polly.configure({
  logging: true,
});
const { server } = polly;

const enhanceLocal = compose(
  connect((state, props) => ({}), {
    updateConfig,
    clearConfig,
  }),
  lifecycle({
    componentDidMount() {
      // Registers any custom API endpoints
      this.props.serverInit(server, testConfig);

      // Safe to call update config now that the server is up
      this.props.updateConfig(testConfig);
    },
    componentWillUnmount() {
      this.props.clearConfig();
    },
  }),
);

const PollyComponent = enhanceLocal(({ children }) => <div className="fill-space">{children}</div>);

export const PollyDecorator = serverInit => storyFn => (
  <PollyComponent serverInit={serverInit}>{storyFn()}</PollyComponent>
);
