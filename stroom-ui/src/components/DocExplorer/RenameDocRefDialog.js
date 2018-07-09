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

import { compose } from 'recompose';
import { connect } from 'react-redux';

import { Modal, Form, Button } from 'semantic-ui-react';

import { actionCreators } from './redux';
import DocPicker from './DocPicker/DocPicker';
import PermissionInheritancePicker from './PermissionInheritancePicker';

const { completeDocRefRename } = actionCreators;

const enhance = compose(connect(
  (state, props) => ({
    isRenaming: state.docExplorer.renameDocRef.isRenaming,
    docRef: state.docExplorer.renameDocRef.docRef,
  }),
  { completeDocRefRename },
));

const RenameDocRefDialog = ({ isRenaming, docRef, completeDocRefRename }) => (
  <Modal open={isRenaming}>
    <Modal.Header>Enter New Name for Doc Ref</Modal.Header>
    <Modal.Content scrolling>
      <Form>
        <Form.Input
          label="Type"
          type="text"
          onChange={(e, value) => console.log('yaas', { e, value })}
          value={docRef ? docRef.name : ''}
        />
      </Form>
    </Modal.Content>
    <Modal.Actions>
      <Button negative onClick={completeDocRefRename}>
        Cancel
      </Button>
      <Button
        positive
        onClick={() => console.log('Implement me please rename dialog')}
        labelPosition="right"
        icon="checkmark"
        content="Choose"
      />
    </Modal.Actions>
  </Modal>
);

export default enhance(RenameDocRefDialog);
