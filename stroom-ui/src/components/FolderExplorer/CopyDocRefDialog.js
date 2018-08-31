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

import { compose, withProps } from 'recompose';
import { connect } from 'react-redux';
import { Field, reduxForm } from 'redux-form';
import { Modal, Button, Form } from 'semantic-ui-react';

import { findItem } from 'lib/treeUtils';
import { actionCreators } from './redux';
import { copyDocuments } from './explorerClient';
import withDocumentTree from './withDocumentTree';

import AppSearchBar from 'components/AppSearchBar';
import PermissionInheritancePicker from 'components/PermissionInheritancePicker';

const { completeDocRefCopy } = actionCreators;

const LISTING_ID = 'copy-item-listing';

const enhance = compose(
  withDocumentTree,
  connect(
    (
      {
        folderExplorer: { documentTree },
        form,
        folderExplorer: {
          copyDocRef: { isCopying, uuids, destinationUuid },
        },
      },
      {},
    ) => {
      const initialDestination = findItem(documentTree, destinationUuid);

      return {
        copyDocRefDialogForm: form.copyDocRefDialog,
        isCopying,
        uuids,
        initialValues: {
          destination: initialDestination && initialDestination.node,
        },
      };
    },
    { completeDocRefCopy, copyDocuments },
  ),
  reduxForm({
    form: 'copyDocRefDialog',
    // We're re-using the same form for each element's modal so we need to permit reinitialization when using the initialValues prop
    enableReinitialize: true,
    touchOnChange: true,
  }),
);

const CopyDocRefDialog = ({
  isCopying,
  uuids,
  completeDocRefCopy,
  copyDocuments,
  copyDocRefDialogForm,
}) => (
  <Modal open={isCopying}>
    <Modal.Header>Select a Destination Folder for the Copy</Modal.Header>
    <Modal.Content scrolling>
      <Form>
        <Form.Field>
          <label>Destination</label>
          <Field
            name="destination"
            component={({ input: { onChange, value } }) => (
              <AppSearchBar pickerId={LISTING_ID} onChange={onChange} value={value} />
            )}
          />
        </Form.Field>
        <Form.Field>
          <label>Permission Inheritance</label>
          <Field
            name="permissionInheritance"
            component={({ input: { onChange, value } }) => (
              <PermissionInheritancePicker onChange={onChange} value={value} />
            )}
          />
        </Form.Field>
      </Form>
    </Modal.Content>
    <Modal.Actions>
      <Button negative onClick={completeDocRefCopy}>
        Cancel
      </Button>
      <Button
        positive
        onClick={() =>
          copyDocuments(
            uuids,
            copyDocRefDialogForm.values.destination.uuid,
            copyDocRefDialogForm.values.permissionInheritance,
          )
        }
        labelPosition="right"
        icon="checkmark"
        content="Choose"
      />
    </Modal.Actions>
  </Modal>
);

export default enhance(CopyDocRefDialog);
