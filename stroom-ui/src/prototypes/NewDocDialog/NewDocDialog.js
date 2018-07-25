import React from 'react';
import { compose, withState, withProps } from 'recompose';
import { connect } from 'react-redux';
import { reduxForm, Field } from 'redux-form';

import { Modal, Header, Button, Icon, Form } from 'semantic-ui-react';
import { InputField } from 'react-semantic-redux-form';

import { required, minLength2 } from 'lib/reduxFormUtils';
import { actionCreators } from './redux';
import {
  DocPickerModal,
  DocRefTypePicker,
  PermissionInheritancePicker,
  explorerClient,
} from 'components/DocExplorer';

const { createDocument } = explorerClient;

const { cancelDocRefCreation } = actionCreators;

const enhance = compose(
  connect(
    ({ newDoc, form: { newDocRef } }, props) => ({
      isOpen: newDoc.isOpen,
      newDocRefValues: newDocRef ? newDocRef.values : {},
    }),
    { cancelDocRefCreation, createDocument },
  ),
  reduxForm({
    form: 'newDocRef',
  }),
);

const NewDocDialog = ({
  isOpen,
  stage,
  cancelDocRefCreation,
  docRefTypes,
  change,
  createDocument,
  newDocRefValues,
}) => (
  <Modal
    open={isOpen}
    onClose={cancelDocRefCreation}
    size="small"
    dimmer="inverted"
    closeOnDimmerClick={false}
  >
    <Header icon="plus" content="Create a New Doc Ref" />
    <Modal.Content>
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
          <label>Destination</label>
          <Field
            name="destination"
            component={({ input: { onChange, value } }) => (
              <DocPickerModal
                pickerId="new-doc-ref-destination"
                foldersOnly
                onChange={({ node, lineage }) => onChange(node)}
              />
            )}
          />
        </Form.Field>
        <Form.Field>
          <label>Permission Inheritance</label>
          <Field
            name="permissionInheritance"
            component={({ input: { onChange, value } }) => (
              <PermissionInheritancePicker
                pickerId="new-doc-ref-permission-inheritance"
                permissionInheritancePicked={(pId, v) => onChange(v)}
                permissionInheritance={value}
              />
            )}
          />
        </Form.Field>
      </Form>
    </Modal.Content>
    <Modal.Actions>
      <Button negative onClick={cancelDocRefCreation} inverted>
        <Icon name="checkmark" /> Cancel
      </Button>
      <Button
        positive
        onClick={() =>
          createDocument(
            newDocRefValues.docRefType,
            newDocRefValues.docRefName,
            newDocRefValues.destination,
            newDocRefValues.permissionInheritance,
          )
        }
        labelPosition="right"
        icon="checkmark"
        content="Choose"
      />
    </Modal.Actions>
  </Modal>
);

export default enhance(NewDocDialog);
