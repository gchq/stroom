import React from 'react';
import PropTypes from 'prop-types';

import { compose } from 'recompose';
import { connect } from 'react-redux';
import { Field, reduxForm } from 'redux-form';

import { Modal, Header, Form, Button } from 'semantic-ui-react';

import { actionCreators } from './redux';

const { pipelineElementAdded } = actionCreators;

const AddElementModal = ({
  pipelineId,
  elementId,
  newElementDefinition,
  pipelineElementAdded,
  setNewElementDefinition,
  newElementForm,
}) => {
  const onConfirmNewElement = () => {
    pipelineElementAdded(pipelineId, elementId, newElementDefinition, newElementForm.values.name);
    setNewElementDefinition(undefined);
  };
  const onCancelNewElement = () => setNewElementDefinition(undefined);

  return (
    <Modal size="tiny" open={!!newElementDefinition} onClose={onCancelNewElement}>
      <Header content="Add New Element" />
      <Modal.Content>
        <Form>
          <Form.Field>
            <label>Name</label>
            <Field name="name" component="input" type="text" placeholder="Name" />
          </Form.Field>
        </Form>
      </Modal.Content>
      <Modal.Actions>
        <Button positive content="Submit" onClick={onConfirmNewElement} />
        <Button negative content="Cancel" onClick={onCancelNewElement} />
      </Modal.Actions>
    </Modal>
  );
};

AddElementModal.propTypes = {
  // Set by container
  pipelineId: PropTypes.string.isRequired,
  elementId: PropTypes.string.isRequired,

  // With New Element Definition from Container
  newElementDefinition: PropTypes.object,
  setNewElementDefinition: PropTypes.func.isRequired,

  // Redux actions
  pipelineElementAdded: PropTypes.func.isRequired,

  // redux form
  newElementForm: PropTypes.object,
};

export default compose(
  connect(
    state => ({
      // state
      newElementForm: state.form.newElementName,
    }),
    { pipelineElementAdded },
  ),
  reduxForm({ form: 'newElementName' }),
)(AddElementModal);
