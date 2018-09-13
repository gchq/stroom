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

import IconHeader from 'components/IconHeader';
import { findItem } from 'lib/treeUtils';
import { actionCreators, defaultListingState } from './redux/copyDocRefReducer';
import { copyDocuments } from './explorerClient';
import withDocumentTree from './withDocumentTree';
import DialogActionButtons from './DialogActionButtons';
import ThemedModal from 'components/ThemedModal';
import AppSearchBar from 'components/AppSearchBar';
import PermissionInheritancePicker from 'components/PermissionInheritancePicker';

const { completeDocRefCopy } = actionCreators;

const LISTING_ID = 'copy-item-listing';

const enhance = compose(
  withDocumentTree,
  connect(
    ({ folderExplorer: { documentTree }, form, folderExplorer: { copyDocRef } }, { listingId }) => {
      const thisCopyState = copyDocRef[listingId] || defaultListingState;
      const { isCopying, uuids, destinationUuid } = thisCopyState;

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
  withHandlers({
    onCancel: ({ completeDocRefCopy, listingId }) => () => completeDocRefCopy(listingId),
    onConfirm: ({
      copyDocuments,
      uuids,
      copyDocRefDialogForm: {
        values: { destination, permissionInheritance },
      },
    }) => () => copyDocuments(uuids, destination.uuid, permissionInheritance),
  }),
);

let CopyDocRefDialog = ({ isCopying, onCancel, onConfirm }) => (
  <ThemedModal
    isOpen={isCopying}
    header={<IconHeader icon="copy" text="Select a Destination Folder for the Copy" />}
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
      <DialogActionButtons onCancel={onCancel} onConfirm={onConfirm} />
    }
  />
);

CopyDocRefDialog = enhance(CopyDocRefDialog);

CopyDocRefDialog.propTypes = {
  listingId: PropTypes.string.isRequired,
};

export default CopyDocRefDialog;
