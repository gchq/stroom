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

const testFolder1 = fromSetupSampleData.children[0];
const testFolder2 = fromSetupSampleData.children[1];
const testDocRef = fromSetupSampleData.children[0].children[0].children[0];

const LISTING_ID = 'test';

// New Doc
const TestNewDocRefDialog = compose(
  connect(({ }) => ({}), { prepareDocRefCreation }),
  lifecycle({
    componentDidMount() {
      const { prepareDocRefCreation, testDestination } = this.props;
      prepareDocRefCreation(LISTING_ID, testDestination);
    },
  }),
)(() => <NewDocRefDialog listingId={LISTING_ID} />);

// Copy
const TestCopyDialog = compose(
  connect(({ }) => ({}), { prepareDocRefCopy }),
  lifecycle({
    componentDidMount() {
      const { prepareDocRefCopy, testUuids, testDestination } = this.props;
      prepareDocRefCopy(LISTING_ID, testUuids, testDestination);
    },
  }),
)(() => <CopyDocRefDialog listingId={LISTING_ID} />);

// Move
const TestMoveDialog = compose(
  connect(({ }) => ({}), { prepareDocRefMove }),
  lifecycle({
    componentDidMount() {
      const { prepareDocRefMove, testUuids, testDestination } = this.props;
      prepareDocRefMove(LISTING_ID, testUuids, testDestination);
    },
  }),
)(() => <MoveDocRefDialog listingId={LISTING_ID} />);

// Delete
const TestDeleteDialog = compose(
  connect(({ }) => ({}), { prepareDocRefDelete }),
  lifecycle({
    componentDidMount() {
      const { prepareDocRefDelete, testUuids } = this.props;
      prepareDocRefDelete(LISTING_ID, testUuids);
    },
  }),
)(() => <DeleteDocRefDialog listingId={LISTING_ID} />);

// Rename
const TestRenameDialog = compose(
  connect(({ }) => ({}), { prepareDocRefRename }),
  lifecycle({
    componentDidMount() {
      const { prepareDocRefRename, testDocRef } = this.props;
      prepareDocRefRename(LISTING_ID, testDocRef);
    },
  }),
)(() => <RenameDocRefDialog listingId={LISTING_ID} />);

storiesOf('Folder Explorer', module)
  .add('Folder explorer', props => <FolderExplorer folderUuid={testFolder1.uuid} />)
  .add('New Doc Ref Dialog', props => <TestNewDocRefDialog testDestination={testFolder2.uuid} />)
  .add('Copy Dialog', props => (
    <TestCopyDialog
      testUuids={testFolder2.children.map(d => d.uuid)}
      testDestination={testFolder2.uuid}
    />
  ))
  .add('Move Dialog', props => (
    <TestMoveDialog
      testUuids={testFolder2.children.map(d => d.uuid)}
      testDestination={testFolder2.uuid}
    />
  ))
  .add('Delete Dialog', props => (
    <TestDeleteDialog testUuids={testFolder2.children.map(d => d.uuid)} />
  ))
  .add('Rename Dialog', props => <TestRenameDialog testDocRef={testDocRef} />);
