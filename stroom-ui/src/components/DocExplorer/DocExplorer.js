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

import { Loader } from 'semantic-ui-react';

import Folder from './Folder';
import MoveDocRefDialog from './MoveDocRefDialog';
import RenameDocRefDialog from './RenameDocRefDialog';
import CopyDocRefDialog from './CopyDocRefDialog';
import DeleteDocRefDialog from './DeleteDocRefDialog';
import DocRefInfoModal from './DocRefInfoModal';
import NewDocDialog from './NewDocDialog';
import { actionCreators } from './redux';
import withExplorerTree from './withExplorerTree';

const { searchTermUpdated, docExplorerOpened } = actionCreators;

const enhance = compose(
  withExplorerTree,
  connect(
    (
      {
        docExplorer: {
          explorerTree: { documentTree, explorers },
        },
      },
      { explorerId },
    ) => ({
      documentTree,
      explorer: explorers[explorerId],
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
      const { docExplorerOpened, explorerId } = this.props;
      const typeFilters = [];
      const allowMultiSelect = true;

      docExplorerOpened(explorerId, allowMultiSelect, typeFilters);
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
  <div className="DocExplorer__container">
    <div className="DocExplorer">
      <NewDocDialog />
      <DocRefInfoModal />
      <MoveDocRefDialog />
      <RenameDocRefDialog />
      <DeleteDocRefDialog />
      <CopyDocRefDialog />
      <Folder explorerId={explorerId} folder={documentTree} />
    </div>
  </div>
);

DocExplorer.propTypes = {
  explorerId: PropTypes.string.isRequired,
};

export default enhance(DocExplorer);
