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
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { compose, lifecycle } from 'recompose';
import { Polly } from '@pollyjs/core';

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

// The server is created as a singular thing for the whole app
// Much easier to manage it this way
const polly = new Polly('Mock Stroom API');
polly.configure({
  logging: true,
});
const { server } = polly;

// The cache acts as a singular global object who's contents are replaced
const testCache = {
  data: {},
};

// This is normally deployed as part of the server
server.get('/config.json').intercept((req, res) => {
  res.json(testConfig);
});

// Explorer Resource
server.get(`${testConfig.explorerServiceUrl}/all`).intercept((req, res) => {
  res.json(testCache.data.documentTree);
});
server.get(`${testConfig.explorerServiceUrl}/docRefTypes`).intercept((req, res) => {
  res.json(testCache.data.docRefTypes);
});

// Elements Resource
server.get(`${testConfig.elementServiceUrl}/elements`).intercept((req, res) => {
  res.json(testCache.data.elements);
});
server.get(`${testConfig.elementServiceUrl}/elementProperties`).intercept((req, res) => {
  res.json(testCache.data.elementProperties);
});

// Pipeline Resource
server.get(`${testConfig.pipelineServiceUrl}/:pipelineId`).intercept((req, res) => {
  const pipeline = testCache.data.pipelines[req.params.pipelineId];
  if (pipeline) {
    res.json(pipeline);
  } else {
    res.sendStatus(404);
  }
});
server
  .post(`${testConfig.pipelineServiceUrl}/:pipelineId`)
  .intercept((req, res) => res.sendStatus(200));

const enhanceLocal = compose(lifecycle({
  componentDidMount() {
    // Replace all the 'server side data' with the properties passed in
    testCache.data = this.props;
  },
}));

const PollyComponent = enhanceLocal(({ children }) => <div className="fill-space">{children}</div>);

PollyComponent.propTypes = {
  documentTree: PropTypes.object,
  docRefTypes: PropTypes.array,
  pipelines: PropTypes.object,
  elements: PropTypes.array,
  elementProperties: PropTypes.object,
};

export const PollyDecorator = props => storyFn => (
  <PollyComponent {...props}>{storyFn()}</PollyComponent>
);
