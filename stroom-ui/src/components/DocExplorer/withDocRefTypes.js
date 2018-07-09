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
 * Higher Order Component that kicks off the fetch of the doc ref types, and waits by rendering a Loader until
 * they are returned.
 */
export default compose(
  connect(
    (state, props) => ({
      isDocRefTypeListReady: state.docExplorer.explorerTree.isDocRefTypeListReady,
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
    renderComponent(() => <Loader active>Awaiting DocRef type list </Loader>),
  ),
);
