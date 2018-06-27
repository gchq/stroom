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

import { compose, lifecycle, branch, renderComponent } from 'recompose';
import { connect } from 'react-redux';

import { Input, Loader } from 'semantic-ui-react';

import Folder from './Folder';
import { actionCreators } from './redux';
import { withTreeReady } from './withExplorerTree';

const { searchTermUpdated, explorerTreeOpened } = actionCreators;

const enhance = compose(
  withTreeReady,
  connect(
    (state, props) => ({
      documentTree: state.explorerTree.documentTree,
      explorer: state.explorerTree.explorers[props.explorerId],
    }),
    {
      searchTermUpdated,
      explorerTreeOpened,
    },
  ),

  branch(
    ({ documentTree }) => !documentTree,
    renderComponent(() => <Loader active>Awaiting Document Tree</Loader>),
  ),
  lifecycle({
    componentDidMount() {
      const {
        explorerTreeOpened,
        explorerId,
        allowMultiSelect,
        allowDragAndDrop,
        typeFilter,
      } = this.props;

      explorerTreeOpened(explorerId, allowMultiSelect, allowDragAndDrop, typeFilter);
    },
  }),
  branch(
    ({ explorer }) => !explorer,
    renderComponent(() => <Loader active>Creating Explorer</Loader>),
  ),
);

const DocExplorer = enhance(({
  documentTree, explorerId, explorer, searchTermUpdated,
}) => (
  <div>
    <Input
      icon="search"
      placeholder="Search..."
      value={explorer.searchTerm}
      onChange={e => searchTermUpdated(explorerId, e.target.value)}
    />
    <Folder explorerId={explorerId} folder={documentTree} />
  </div>
));

DocExplorer.propTypes = {
  explorerId: PropTypes.string.isRequired,
};

export default DocExplorer;
