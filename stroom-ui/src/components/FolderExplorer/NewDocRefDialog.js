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
import { reduxForm, Field } from 'redux-form';

import IconHeader from 'components/IconHeader';
import ThemedModal from 'components/ThemedModal';
import DialogActionButtons from './DialogActionButtons';
import { required, minLength2 } from 'lib/reduxFormUtils';
import { actionCreators, defaultListingState } from './redux/newDocReducer';
import { DocRefTypePicker } from 'components/DocRefTypes';
import explorerClient from 'components/FolderExplorer/explorerClient';
import PermissionInheritancePicker from 'components/PermissionInheritancePicker';

const { createDocument } = explorerClient;

const { completeDocRefCreation } = actionCreators;

const enhance = compose(
  connect(
    ({ userSettings: { theme }, folderExplorer: { newDoc }, form }, { listingId }) => ({
      theme,
      ...(newDoc[listingId] || defaultListingState),
      newDocRefForm: form.newDocRef,
    }),
    { completeDocRefCreation, createDocument },
  ),
  reduxForm({
    form: 'newDocRef',
    enableReinitialize: true,
    touchOnChange: true,
  }),
  withHandlers({
    onConfirm: ({
      destination,
      newDocRefForm: {
        values: { docRefType, docRefName, permissionInheritance },
      },
    }) => () => createDocument(docRefType, docRefName, destination, permissionInheritance),
    onCancel: ({ completeDocRefCreation, listingId }) => () => completeDocRefCreation(listingId),
  }),
);

let NewDocRefDialog = ({
  isOpen,
  stage,
  createDocument,
  destination,
  // We need to include the theme because modals are mounted outside the root
  // div, i.e. outside the div which contains the theme class.
  theme,
  onCancel,
  onConfirm,
}) => (
  <ThemedModal
    isOpen={isOpen}
    onClose={completeDocRefCreation}
    size="small"
    closeOnDimmerClick={false}
    header={
      <IconHeader icon="plus" text={`Create a New Doc Ref in ${destination && destination.name}`} />
    }
    content={
      <form>
        <div>
          <label>Doc Ref Type</label>
          <Field
            name="docRefType"
            component={({ input: { onChange, value } }) => (
              <DocRefTypePicker pickerId="new-doc-ref-type" onChange={onChange} value={value} />
            )}
          />
        </div>
        <div>
          <label>Name</label>
          <Field
            name="docRefName"
            component="input"
            type="text"
            placeholder="Name"
            validate={[required, minLength2]}
          />
        </div>
        <div>
          <label>Permission Inheritance</label>
          <Field
            className="raised-border"
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

NewDocRefDialog = enhance(NewDocRefDialog);

NewDocRefDialog.propTypes = {
  listingId: PropTypes.string.isRequired,
};

export default NewDocRefDialog;
