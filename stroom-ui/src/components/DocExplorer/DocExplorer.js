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
import MoveDocRefDialog from './MoveDocRefDialog';
import { actionCreators } from './redux/docExplorerReducer';
import withExplorerTree from './withExplorerTree';

const { searchTermUpdated, docExplorerOpened } = actionCreators;

const enhance = compose(
  withExplorerTree,
  connect(
    (state, props) => ({
      documentTree: state.docExplorer.documentTree,
      explorer: state.docExplorer.explorers[props.explorerId],
    }),
    {
      searchTermUpdated,
      docExplorerOpened,
    },
  ),

  branch(
    ({ documentTree }) => !documentTree,
    renderComponent(() => <Loader active>Awaiting Document Tree</Loader>),
  ),
  lifecycle({
    componentDidMount() {
      const {
        docExplorerOpened,
        explorerId,
        allowMultiSelect,
        allowDragAndDrop,
        typeFilter,
      } = this.props;

      docExplorerOpened(explorerId, allowMultiSelect, allowDragAndDrop, typeFilter);
    },
  }),
  branch(
    ({ explorer }) => !explorer,
    renderComponent(() => <Loader active>Creating Explorer</Loader>),
  ),
);

const DocExplorer = ({
  documentTree, explorerId, explorer, searchTermUpdated,
}) => (
  <div>
    <MoveDocRefDialog explorerId={explorerId} />
    <Input
      icon="search"
      placeholder="Search..."
      value={explorer.searchTerm}
      onChange={e => searchTermUpdated(explorerId, e.target.value)}
    />
    <Folder explorerId={explorerId} folder={documentTree} />
  </div>
);

DocExplorer.propTypes = {
  explorerId: PropTypes.string.isRequired,
};

export default enhance(DocExplorer);
