import React from 'react';
import { compose } from 'recompose';
import { connect } from 'react-redux';
import { reduxForm, Field } from 'redux-form';

import { Modal, Header, Button, Icon, Form } from 'semantic-ui-react';
import { InputField } from 'react-semantic-redux-form';

import ThemedModal from 'components/ThemedModal';
import { required, minLength2 } from 'lib/reduxFormUtils';
import { actionCreators } from './redux';
import { DocRefTypePicker } from 'components/DocRefTypes';
import explorerClient from 'components/FolderExplorer/explorerClient';

import PermissionInheritancePicker from 'components/PermissionInheritancePicker';

const { createDocument } = explorerClient;

const { completeDocRefCreation } = actionCreators;

const enhance = compose(
  connect(
    ({
      userSettings: { theme },
      folderExplorer: {
        newDoc: { isOpen, destination },
      },
      form,
    }) => ({
      theme,
      isOpen,
      destination,
      newDocRefForm: form.newDocRef,
    }),
    { completeDocRefCreation, createDocument },
  ),
  reduxForm({
    form: 'newDocRef',
    enableReinitialize: true,
    touchOnChange: true,
  }),
);

const NewDocDialog = ({
  isOpen,
  stage,
  completeDocRefCreation,
  createDocument,
  newDocRefForm,
  destination,
  // We need to include the theme because modals are mounted outside the root
  // div, i.e. outside the div which contains the theme class.
  theme,
}) => (
  <ThemedModal
    open={isOpen}
    onClose={completeDocRefCreation}
    size="small"
    closeOnDimmerClick={false}
    header={
      <Header icon="plus" content={`Create a New Doc Ref in ${destination && destination.name}`} />
    }
    content={
      <Form>
        <Form.Field>
          <label>Doc Ref Type</label>
          <Field
            name="docRefType"
            component={({ input: { onChange, value } }) => (
              <DocRefTypePicker pickerId="new-doc-ref-type" onChange={onChange} value={value} />
            )}
          />
        </Form.Field>
        <Form.Field>
          <label>Name</label>
          <Field
            name="docRefName"
            component={InputField}
            type="text"
            placeholder="Name"
            validate={[required, minLength2]}
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
        <Button negative onClick={completeDocRefCreation}>
          <Icon name="checkmark" /> Cancel
        </Button>
        <Button
          positive
          onClick={() =>
            createDocument(
              newDocRefForm.values.docRefType,
              newDocRefForm.values.docRefName,
              destination,
              newDocRefForm.values.permissionInheritance,
            )
          }
          labelPosition="right"
          icon="checkmark"
          content="Choose"
        />
      </React.Fragment>
    }
  />
);

export default enhance(NewDocDialog);
