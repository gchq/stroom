import React from 'react';
import PropTypes from 'prop-types';

import { compose, withHandlers } from 'recompose';
import { connect } from 'react-redux';
import { reduxForm, Field } from 'redux-form';

import IconHeader from 'components/IconHeader';
import Button from 'components/Button';
import ThemedModal from 'components/ThemedModal';
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
      <React.Fragment>
        <Button icon="times" onClick={onCancel} text="Cancel" />
        <Button onClick={onConfirm} icon="check" text="Choose" />
      </React.Fragment>
    }
  />
);

NewDocRefDialog = enhance(NewDocRefDialog);

NewDocRefDialog.propTypes = {
  listingId: PropTypes.string.isRequired,
};

export default NewDocRefDialog;
