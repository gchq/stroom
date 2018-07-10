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

import { findItem } from 'lib/treeUtils';
import { actionCreators as fetchActionCreators } from 'lib/fetchTracker.redux';

const { resetAllUrls } = fetchActionCreators;

export const DevServerDecorator = storyFn => <DevServerComponent>{storyFn()}</DevServerComponent>;

const testConfig = {
  authenticationServiceUrl: '/authService/authentication/v1',
  authorisationServiceUrl: '/api/authorisation/v1',
  streamTaskServiceUrl: '/api/streamtasks/v1',
  pipelineServiceUrl: '/api/pipelines/v1',
  xsltServiceUrl: '/api/xslt/v1',
  explorerServiceUrl: '/api/explorer/v1',
  elementServiceUrl: '/api/elements/v1',
  authUsersUiUrl:
    'auth/users/because/they/are/loaded/in/an/iframe/which/is/beyond/scope/of/these/tests',
  authTokensUiUrl:
    'auth/tokens/because/they/are/loaded/in/an/iframe/which/is/beyond/scope/of/these/tests',
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

const startTime = Date.now();

// Hot loading should pass through
server.get('*.hot-update.json').passthrough();

// This is normally deployed as part of the server
server.get('/config.json').intercept((req, res) => {
  res.json(testConfig);
});

// Explorer Resource
// // Get Explorer Tree
server.get(`${testConfig.explorerServiceUrl}/all`).intercept((req, res) => {
  res.json(testCache.data.documentTree);
});
// // Get Info
server
  .get(`${testConfig.explorerServiceUrl}/info/:docRefType/:docRefUuid`)
  .intercept((req, res) => {
    const docRef = findItem(testCache.data.documentTree, req.params.docRefUuid);
    const info = {
      docRef,
      createTime: startTime,
      updateTime: Date.now(),
      createUser: 'testGuy',
      updateUser: 'testGuy',
      otherInfo: 'pet peeves - crying babies',
    };
    res.json(info);
  });
// // Get Document Types
server.get(`${testConfig.explorerServiceUrl}/docRefTypes`).intercept((req, res) => {
  res.json(testCache.data.docRefTypes);
});
// // Copy Document
server.post(`${testConfig.explorerServiceUrl}/copy`).intercept((req, res) => {
  res.sendStatus(200);
});
// // Move Document
server.put(`${testConfig.explorerServiceUrl}/move`).intercept((req, res) => {
  res.sendStatus(200);
});
// // Rename Document
server.put(`${testConfig.explorerServiceUrl}/rename`).intercept((req, res) => {
  res.sendStatus(200);
});
// // Delete Document
server.delete(`${testConfig.explorerServiceUrl}/delete`).intercept((req, res) => {
  res.sendStatus(200);
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

// XSLT Resource
server.get(`${testConfig.xsltServiceUrl}/:xsltId`).intercept((req, res) => {
  const xslt = testCache.data.xslt[req.params.xsltId];
  if (xslt) {
    res.setHeader('Content-Type', 'application/xml');
    res.send(xslt);
  } else {
    res.sendStatus(404);
  }
});
server.post(`${testConfig.xsltServiceUrl}/:xsltId`).intercept((req, res) => res.sendStatus(200));

// Stream Task Resource (for Tracker Dashboard)
server.get(`${testConfig.streamTaskServiceUrl}/`).intercept((req, res) =>
  res.json({
    streamTasks: testCache.data.trackers || [],
    totalStreamTasks: testCache.data.trackers ? testCache.data.trackers.length : 0,
  }));

const enhanceLocal = compose(
  connect(state => ({}), {
    resetAllUrls,
  }),
  lifecycle({
    componentWillMount() {
      // must be done before any children have mounted, but the docs say this function is unsafe...
      // We can't hook the constructor in the lifecycle thing, so if we need to replace this later then
      // we can make a little custom component
      this.props.resetAllUrls();
      testCache.data = {}; // force clear, each test must set it's own stuff up
    },
    componentDidMount() {
      // Replace all the 'server side data' with the properties passed in
      testCache.data = this.props;
    },
  }),
);

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
