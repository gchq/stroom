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

import { compose, withHandlers } from 'recompose';
import { connect } from 'react-redux';
import { Field, reduxForm } from 'redux-form';

import Button from 'components/Button';
import IconHeader from 'components/IconHeader';
import { findItem } from 'lib/treeUtils';
import { actionCreators, defaultListingState } from './redux/moveDocRefReducer';
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
    ({ folderExplorer: { documentTree }, form, folderExplorer: { moveDocRef } }, { listingId }) => {
      const thisState = moveDocRef[listingId] || defaultListingState;
      const { isMoving, uuids, destinationUuid } = thisState;
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
  withHandlers({
    onConfirm: ({
      moveDocuments,
      uuids,
      moveDocRefDialogForm: {
        values: { destination, permissionInheritance },
      },
    }) => () => moveDocuments(uuids, destination.uuid, permissionInheritance),
    onCancel: ({ listingId, completeDocRefMove }) => () => completeDocRefMove(listingId),
  }),
);

let MoveDocRefDialog = ({ isMoving, onConfirm, onCancel }) => (
  <ThemedModal
    isOpen={isMoving}
    header={<IconHeader icon="arrows-alt" text="Select a Destination Folder for the Move?" />}
    content={
      <form>
        <div>
          <label>Destination</label>
          <Field
            name="destination"
            component={({ input: { onChange, value } }) => (
              <AppSearchBar pickerId={LISTING_ID} onChange={onChange} value={value} />
            )}
          />
        </div>
        <div>
          <label>Permission Inheritance</label>
          <Field
            name="permissionInheritance"
            component={({ input: { onChange, value } }) => (
              <PermissionInheritancePicker onChange={onChange} value={value} />
            )}
          />
        </div>
      </form>
    }
    actions={
      <React.Fragment>
        <Button onClick={onCancel} icon='times' text="Cancel" />
        <Button onClick={onConfirm} icon="check" text="Choose" />
      </React.Fragment>
    }
  />
);

MoveDocRefDialog = enhance(MoveDocRefDialog);

MoveDocRefDialog.propTypes = {
  listingId: PropTypes.string.isRequired,
};

export default MoveDocRefDialog;
