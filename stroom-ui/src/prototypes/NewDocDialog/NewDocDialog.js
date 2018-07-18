import React from 'react';
import { compose, withState, withProps } from 'recompose';
import { connect } from 'react-redux';
import { reduxForm, Field } from 'redux-form';

import { Modal, Header, Button, Icon, Form } from 'semantic-ui-react';
import { InputField } from 'react-semantic-redux-form';

import { required, minLength2 } from 'lib/reduxFormUtils';
import { actionCreators } from './redux';
import { DocPickerModal, DocRefTypePicker } from 'components/DocExplorer';
import createNewDocRef from './createNewDocRef';

const { cancelDocRefCreation } = actionCreators;

const enhance = compose(
  connect(
    ({ newDoc, form }, props) => ({
      isOpen: newDoc.isOpen,
    }),
    { cancelDocRefCreation, createNewDocRef },
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
  createNewDocRef,
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
            name="name"
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
      </Form>
    </Modal.Content>
    <Modal.Actions>
      <Button negative onClick={cancelDocRefCreation} inverted>
        <Icon name="checkmark" /> Cancel
      </Button>
      <Button
        positive
        onClick={createNewDocRef}
        labelPosition="right"
        icon="checkmark"
        content="Choose"
      />
    </Modal.Actions>
  </Modal>
);

export default enhance(NewDocDialog);
