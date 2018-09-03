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
import { Header, Button, Form } from 'semantic-ui-react';

import { findItem } from 'lib/treeUtils';
import { actionCreators } from './redux';
import { moveDocuments } from './explorerClient';
import withDocumentTree from './withDocumentTree';

import AppSearchBar from 'components/AppSearchBar';
import ThemedModal from 'components/ThemedModal';
import PermissionInheritancePicker from 'components/PermissionInheritancePicker';

const { completeDocRefMove } = actionCreators;

const LISTING_ID = 'move-item-listing';

const enhance = compose(
  withDocumentTree,
  connect(
    ({
      folderExplorer: { documentTree },
      form,
      folderExplorer: {
        moveDocRef: { isMoving, uuids, destinationUuid },
      },
    }) => {
      const initialDestination = findItem(documentTree, destinationUuid);

      return {
        moveDocRefDialogForm: form.moveDocRefDialog,
        isMoving,
        uuids,
        initialValues: {
          destination: initialDestination && initialDestination.node,
        },
      };
    },
    { completeDocRefMove, moveDocuments },
  ),
  reduxForm({
    form: 'moveDocRefDialog',
    // We're re-using the same form for each element's modal so we need to permit reinitialization when using the initialValues prop
    enableReinitialize: true,
    touchOnChange: true,
  }),
);

const MoveDocRefDialog = ({
  isMoving,
  uuids,
  completeDocRefMove,
  moveDocuments,
  moveDocRefDialogForm,
}) => (
  <ThemedModal
    open={isMoving}
    header={
      <Header className="header" icon="move" content="Select a Destination Folder for the Move?" />
    }
    content={
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
    }
    actions={
      <React.Fragment>
        <Button negative onClick={completeDocRefMove}>
          Cancel
        </Button>
        <Button
          positive
          onClick={() => {
            moveDocuments(
              uuids,
              moveDocRefDialogForm.values.destination.uuid,
              moveDocRefDialogForm.values.permissionInheritance,
            );
          }}
          labelPosition="right"
          icon="checkmark"
          content="Choose"
        />
      </React.Fragment>
    }
  />
);

export default enhance(MoveDocRefDialog);
