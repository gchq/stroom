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

import { Modal, Form, Button } from 'semantic-ui-react';

import { actionCreators } from './redux';
import { renameDocument } from 'components/FolderExplorer/explorerClient';

const { completeDocRefRename, renameUpdated } = actionCreators;

const enhance = connect(
  (
    {
      folderExplorer: {
        renameDocRef: { isRenaming, docRef, name },
      },
    },
    props,
  ) => ({
    isRenaming,
    docRef,
    name,
  }),
  { completeDocRefRename, renameDocument, renameUpdated },
);

const RenameDocRefDialog = ({
  name,
  renameUpdated,
  isRenaming,
  docRef,
  completeDocRefRename,
  renameDocument,
}) => (
  <Modal open={isRenaming}>
    <Modal.Header>Enter New Name for Doc Ref</Modal.Header>
    <Modal.Content scrolling>
      <Form>
        <Form.Input
          label="Type"
          type="text"
          onChange={(e, { value }) => renameUpdated(value)}
          value={name || (docRef ? docRef.name : '')}
        />
      </Form>
    </Modal.Content>
    <Modal.Actions>
      <Button negative onClick={completeDocRefRename}>
        Cancel
      </Button>
      <Button
        positive
        onClick={() => renameDocument(docRef, name)}
        labelPosition="right"
        icon="checkmark"
        content="Choose"
      />
    </Modal.Actions>
  </Modal>
);

export default enhance(RenameDocRefDialog);
