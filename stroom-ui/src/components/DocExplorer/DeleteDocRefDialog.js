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

import { Modal, Button } from 'semantic-ui-react';

import { actionCreators } from './redux';
import DocPicker from './DocPicker/DocPicker';
import PermissionInheritancePicker from './PermissionInheritancePicker';

const { completeDocRefDelete } = actionCreators;

const enhance = compose(connect(
  (state, props) => ({
    isDeleting: state.docExplorer.deleteDocRef.isDeleting,
    docRefs: state.docExplorer.deleteDocRef.docRefs,
  }),
  { completeDocRefDelete },
));

const DeleteDocRefDialog = ({
  explorerId, isDeleting, docRefs, completeDocRefDelete,
}) => (
  <Modal open={isDeleting}>
    <Modal.Header>Are you sure about deleting these Doc Refs?</Modal.Header>
    <Modal.Content scrolling />
    <Modal.Actions>
      <Button negative onClick={completeDocRefDelete}>
        Cancel
      </Button>
      <Button
        positive
        onClick={() => console.log('Please implement me delete dialog')}
        labelPosition="right"
        icon="checkmark"
        content="Choose"
      />
    </Modal.Actions>
  </Modal>
);

DeleteDocRefDialog.propTypes = {
  explorerId: PropTypes.string.isRequired,
};

export default enhance(DeleteDocRefDialog);
