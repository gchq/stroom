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
import { compose, lifecycle, branch, renderComponent } from 'recompose';
import { Polly } from '@pollyjs/core';
import * as JsSearch from 'js-search';

import { guid, findItem, addItemsToTree, findByUuids, deleteItemsFromTree, iterateNodes } from 'lib/treeUtils';
import { actionCreators as fetchActionCreators } from 'lib/fetchTracker.redux';
import withConfig from 'startup/withConfig';

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
// Search
server.post(`${testConfig.explorerServiceUrl}/search`).intercept((req, res) => {
  const { searchTerm } = JSON.parse(req.body);
  let searchResults = [];

  const allDocuments = [];
  iterateNodes(testCache.data.documentTree, (lineage, node) => {
    allDocuments.push({
      name: node.name,
      type: node.type,
      uuid: node.uuid,
      lineage,
      lineageNames: lineage.reduce((acc, curr) => `${acc} ${curr.name}`, ''),
    });
  });

  const search = new JsSearch.Search('uuid');
  search.addIndex('name');
  search.addIndex('lineageNames');
  search.addDocuments(allDocuments);

  console.log('Searching', {searchTerm, tree: testCache.data.documentTree});

  if (searchTerm && searchTerm.length > 1) {
    searchResults = search.search(searchTerm).map(s => ({
      node: {
        name: s.name,
        type: s.type,
        uuid: s.uuid,
        lineage: s.lineage,
      },
    }));
  }

  res.json(searchResults);
});
// // Get Info
server
  .get(`${testConfig.explorerServiceUrl}/info/:docRefType/:docRefUuid`)
  .intercept((req, res) => {
    const { node: docRef } = findItem(testCache.data.documentTree, req.params.docRefUuid);
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
// // Create Document
server.post(`${testConfig.explorerServiceUrl}/create`).intercept((req, res) => {
  const { docRefType, docRefName, destinationFolderRef, permissionInheritance } = JSON.parse(req.body);

  let newDocRef = {
    uuid: guid(),
    type: docRefType,
    name: docRefName,
    children: (docRefType === 'Folder') ? [] : undefined
  }
  testCache.data.documentTree = addItemsToTree(testCache.data.documentTree, destinationFolderRef.uuid, [newDocRef]);

  res.json(testCache.data.documentTree);
});

// Copies need to be deep
const copyDocRef = docRef => ({
  uuid: guid(),
  type: docRef.type,
  name: `${docRef.name}-copy-${guid()}`,
  children: docRef.children ? docRef.children.map(copyDocRef) : undefined
})

// Copy Document
server.post(`${testConfig.explorerServiceUrl}/copy`).intercept((req, res) => {
  const { destinationFolderRef, docRefs } = JSON.parse(req.body);

  const copies = docRefs.map(d => findItem(testCache.data.documentTree, d.uuid)).map(d => d.node).map(copyDocRef);
  testCache.data.documentTree = addItemsToTree(testCache.data.documentTree, destinationFolderRef.uuid, copies);

  res.json(testCache.data.documentTree);
});
// Move Document
server.put(`${testConfig.explorerServiceUrl}/move`).intercept((req, res) => {
  const { destinationFolderRef, docRefs } = JSON.parse(req.body);

  let docRefUuidsToDelete = docRefs.map(d => d.uuid);
  let itemsToMove = findByUuids(testCache.data.documentTree, docRefUuidsToDelete);
  testCache.data.documentTree = deleteItemsFromTree(testCache.data.documentTree, docRefUuidsToDelete);
  testCache.data.documentTree = addItemsToTree(testCache.data.documentTree, destinationFolderRef.uuid, itemsToMove);

  res.json(testCache.data.documentTree);
});
// Rename Document
server.put(`${testConfig.explorerServiceUrl}/rename`).intercept((req, res) => {
  const { docRef, name } = JSON.parse(req.body);
  res.json({ ...docRef, name });
});
// Delete Document
server.delete(`${testConfig.explorerServiceUrl}/delete`).intercept((req, res) => {
  const docRefs = JSON.parse(req.body);
  res.json(testCache.data.documentTree);
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
server.get(`${testConfig.pipelineServiceUrl}/`).intercept((req, res) => {
  res.json({
    total: Object.keys(testCache.data.pipelines).length,
    pipelines: Object.keys(testCache.data.pipelines).map(p => ({
      uuid: p,
      name: p,
      type: 'Pipeline',
    })),
  });
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
  connect(({config}) => ({config}), {
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
      testCache.data = {
        documentTree: {...this.props.documentTree},
        docRefTypes: [...this.props.docRefTypes],
        pipelines: {...this.props.pipelines},
        elements: [...this.props.elements],
        elementProperties: {...this.props.elementProperties},
        xslt: {...this.props.xslt},
        trackers: [...this.props.trackers]
      };
    },
  }),
  withConfig
);

const PollyComponent = enhanceLocal(({ children }) => <div className="fill-space">{children}</div>);

PollyComponent.propTypes = {
  documentTree: PropTypes.object.isRequired,
  docRefTypes: PropTypes.array.isRequired,
  pipelines: PropTypes.object.isRequired,
  elements: PropTypes.array.isRequired,
  elementProperties: PropTypes.object.isRequired,
  xslt: PropTypes.object.isRequired,
  trackers: PropTypes.array.isRequired
};

PollyComponent.defaultProps = {
  documentTree: {},
  docRefTypes: [],
  pipelines: {},
  elements: [],
  elementProperties: {},
  xslt: {},
  trackers: []
}

export const PollyDecorator = props => storyFn => (
  <PollyComponent {...props}>{storyFn()}</PollyComponent>
);
