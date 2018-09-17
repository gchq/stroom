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

import { connect } from 'react-redux';
import { compose, withHandlers } from 'recompose';
import { Field, reduxForm } from 'redux-form';

import DialogActionButtons from './DialogActionButtons';
import IconHeader from 'components/IconHeader';
import { actionCreators, defaultListingState } from './redux/renameDocRefReducer';
import { renameDocument } from 'components/FolderExplorer/explorerClient';
import ThemedModal from 'components/ThemedModal';
import { required, minLength2 } from 'lib/reduxFormUtils';

const { completeDocRefRename } = actionCreators;

const enhance = compose(
  connect(
    ({ folderExplorer: { renameDocRef }, form }, { docRef, listingId }) => ({
      ...(renameDocRef[listingId] || defaultListingState),
      renameDocRefForm: form.renameDocRefDialog,
      initialValues: {
        docRefName: docRef ? docRef.name : '',
      },
    }),
    { completeDocRefRename, renameDocument },
  ),
  reduxForm({
    form: 'renameDocRefDialog',
    // We're re-using the same form for each element's modal so we need to permit reinitialization when using the initialValues prop
    enableReinitialize: true,
    touchOnChange: true,
  }),
  withHandlers({
    onConfirm: ({
      renameDocument,
      docRef,
      renameDocRefForm: {
        values: { docRefName },
      },
    }) => () => renameDocument(docRef, docRefName),
    onCancel: ({ completeDocRefRename, listingId }) => () => completeDocRefRename(listingId),
  }),
);

let RenameDocRefDialog = ({ isRenaming, onConfirm, onCancel }) => (
  <ThemedModal
    isOpen={isRenaming}
    header={<IconHeader icon="edit" text="Enter New Name for Doc Ref" />}
    content={
      <form>
        <label>Type</label>
        <Field
          name="docRefName"
          component="input"
          type="text"
          placeholder="Name"
          validate={[required, minLength2]}
        />
      </form>
    }
    actions={<DialogActionButtons onCancel={onCancel} onConfirm={onConfirm} />}
  />
);

RenameDocRefDialog = enhance(RenameDocRefDialog);

RenameDocRefDialog.propTypes = {
  listingId: PropTypes.string.isRequired,
};

export default RenameDocRefDialog;
