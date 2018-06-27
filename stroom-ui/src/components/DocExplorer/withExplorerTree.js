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
import { connect } from 'react-redux';
import { branch, compose, renderComponent, lifecycle } from 'recompose';
import { Loader } from 'semantic-ui-react';
import { fetchDocTree } from './explorerClient';
import { withConfigReady } from 'startup/config';

const loader = <Loader active>Awaiting explorer tree data </Loader>;

/**
 * Higher Order Component that ensures that the explorer tree has been set.
 */
const withTreeReady = compose(
  connect(
    (state, props) => ({
      treeIsReady: state.explorerTree.isTreeReady,
    }),
    {},
  ),
  branch(({ treeIsReady }) => !treeIsReady, renderComponent(() => loader)),
);

/**
 * Higher Order Component that kicks off the fetch of the doc tree, and waits by rendering a Loader until
 * the tree is returned.
 */
const requestTreeAndWait = compose(
  connect(
    (state, props) => ({
      treeIsReady: state.explorerTree.isTreeReady,
    }),
    {
      fetchDocTree,
    },
  ),
  withConfigReady,
  lifecycle({
    componentDidMount() {
      this.props.fetchDocTree();
    },
  }),
  branch(({ treeIsReady }) => !treeIsReady, renderComponent(() => loader)),
);

export { withTreeReady, requestTreeAndWait };
