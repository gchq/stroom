import React from 'react';
import PropTypes from 'prop-types';

import { compose } from 'recompose';
import { connect } from 'react-redux';
import { Field, reduxForm } from 'redux-form';

import { Modal, Header, Form, Button } from 'semantic-ui-react';

import { actionCreators } from './redux';

import { uniqueElementName } from './pipelineUtils';
import { withPipeline } from './withPipeline';
import { required, minLength2, renderField } from 'lib/reduxFormUtils';

const { pipelineElementAdded } = actionCreators;

const AddElementModal = ({
  // Set by container
  pipelineId,
  elementId,

  // withPipeline
  pipeline,

  // Redux actions
  pipelineElementAdded,

  // withNewElementDefinition from container
  newElementDefinition,
  setNewElementDefinition,

  // Redux form
  newElementForm,
  invalid,
  submitting,
  reset,
}) => {
  const onConfirmNewElement = () => {
    pipelineElementAdded(pipelineId, elementId, newElementDefinition, newElementForm.values.name);
    setNewElementDefinition(undefined);
    reset();
  };
  const onCancelNewElement = () => {
    setNewElementDefinition(undefined);
    reset();
  };

  return (
    <Modal size="tiny" open={!!newElementDefinition} onClose={onCancelNewElement}>
      <Header content="Add New Element" />
      <Modal.Content>
        <Form>
          <Form.Field>
            <label>Name</label>
            <Field
              name="name"
              component={renderField}
              type="text"
              placeholder="Name"
              validate={[required, minLength2, uniqueElementName(pipeline)]}
            />
          </Form.Field>
        </Form>
      </Modal.Content>
      <Modal.Actions>
        <Button
          positive
          content="Submit"
          disabled={invalid || submitting}
          onClick={onConfirmNewElement}
        />
        <Button negative content="Cancel" onClick={onCancelNewElement} />
      </Modal.Actions>
    </Modal>
  );
};

AddElementModal.propTypes = {
  // Set by container
  pipelineId: PropTypes.string.isRequired,
  elementId: PropTypes.string.isRequired,

  // withPipeline
  pipeline: PropTypes.object.isRequired,

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
  withPipeline(),
)(AddElementModal);
