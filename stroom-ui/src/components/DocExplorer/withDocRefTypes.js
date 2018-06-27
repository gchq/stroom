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
import { fetchDocRefTypes } from './explorerClient';

/**
 * Higher Order Component that ensures that the doc ref types have been set.
 */
const withDocRefTypesReady = compose(
  connect(
    (state, props) => ({
      isDocRefTypeListReady: state.explorerTree.isDocRefTypeListReady,
    }),
    {},
  ),
  branch(
    ({ isDocRefTypeListReady }) => !isDocRefTypeListReady,
    renderComponent(() => <Loader active />),
  ),
);

/**
 * Higher Order Component that kicks off the fetch of the doc ref types, and waits by rendering a Loader until
 * they are returned.
 */
const requestDocRefTypesAndWait = compose(
  connect(
    (state, props) => ({
      isDocRefTypeListReady: state.explorerTree.isDocRefTypeListReady,
    }),
    {
      fetchDocRefTypes,
    },
  ),
  lifecycle({
    componentDidMount() {
      this.props.fetchDocRefTypes();
    },
  }),
  branch(
    ({ isDocRefTypeListReady }) => !isDocRefTypeListReady,
    renderComponent(() => <Loader active />),
  ),
);

export { withDocRefTypesReady, requestDocRefTypesAndWait };
