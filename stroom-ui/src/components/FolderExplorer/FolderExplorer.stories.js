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
import PropTypes from 'prop-types';
import { compose, lifecycle } from 'recompose';
import { connect } from 'react-redux';

import { storiesOf, addDecorator } from '@storybook/react';
import { Header } from 'semantic-ui-react/dist/commonjs';

import { fromSetupSampleData } from './test';
import { actionCreators } from './redux';
import FolderExplorer from './FolderExplorer';
import NewDocRefDialog from './NewDocRefDialog';
import CopyDocRefDialog from './CopyDocRefDialog';
import MoveDocRefDialog from './MoveDocRefDialog';
import RenameDocRefDialog from './RenameDocRefDialog';
import DeleteDocRefDialog from './DeleteDocRefDialog';

const {
  prepareDocRefCreation,
  prepareDocRefCopy,
  prepareDocRefMove,
  prepareDocRefRename,
  prepareDocRefDelete,
} = actionCreators;

import 'styles/main.css';

const LISTING_ID = 'test';

// New Doc
const TestNewDocRefDialog = compose(
  connect(({}) => ({}), { prepareDocRefCreation }),
  lifecycle({
    componentDidMount() {
      const { prepareDocRefCreation, testDestination } = this.props;
      prepareDocRefCreation(LISTING_ID, testDestination);
    },
  }),
)(() => <NewDocRefDialog listingId={LISTING_ID} />);

// Copy
const TestCopyDialog = compose(
  connect(({}) => ({}), { prepareDocRefCopy }),
  lifecycle({
    componentDidMount() {
      const { prepareDocRefCopy, testUuids, testDestination } = this.props;
      prepareDocRefCopy(LISTING_ID, testUuids, testDestination);
    },
  }),
)(() => <CopyDocRefDialog listingId={LISTING_ID} />);

// Move
const TestMoveDialog = compose(
  connect(({}) => ({}), { prepareDocRefMove }),
  lifecycle({
    componentDidMount() {
      const { prepareDocRefMove, testUuids, testDestination } = this.props;
      prepareDocRefMove(LISTING_ID, testUuids, testDestination);
    },
  }),
)(() => <MoveDocRefDialog listingId={LISTING_ID} />);

// Delete
const TestDeleteDialog = compose(
  connect(({}) => ({}), { prepareDocRefDelete }),
  lifecycle({
    componentDidMount() {
      const { prepareDocRefDelete, testUuids } = this.props;
      prepareDocRefDelete(LISTING_ID, testUuids);
    },
  }),
)(() => <DeleteDocRefDialog listingId={LISTING_ID} />);

// Rename
const TestRenameDialog = compose(
  connect(({}) => ({}), { prepareDocRefRename }),
  lifecycle({
    componentDidMount() {
      const { prepareDocRefRename, testDocRef } = this.props;
      prepareDocRefRename(LISTING_ID, testDocRef);
    },
  }),
)(() => <RenameDocRefDialog listingId={LISTING_ID} />);

storiesOf('Folder Explorer', module)
  .add('Folder explorer', props => <FolderExplorer folderUuid="pipelines1234567890" />)
  .add('New Doc Ref Dialog', props => <TestNewDocRefDialog testDestination="pipelines1234567890" />)
  .add('Copy Dialog', props => (
    <TestCopyDialog
      testUuids={['dictionaries1234567890', 'xslt1234567890']}
      testDestination="pipelines1234567890"
    />
  ))
  .add('Move Dialog', props => (
    <TestMoveDialog
      testUuids={['dictionaries1234567890', 'xslt1234567890']}
      testDestination="pipelines1234567890"
    />
  ))
  .add('Delete Dialog', props => (
    <TestDeleteDialog testUuids={['dictionaries1234567890', 'xslt1234567890']} />
  ))
  .add('Rename Dialog', props => <TestRenameDialog testDocRef={fromSetupSampleData} />);
