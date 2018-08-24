import React from 'react';
import PropTypes from 'prop-types';

import { compose, withProps } from 'recompose';
import { connect } from 'react-redux';
import { Field, reduxForm } from 'redux-form';

import { Modal, Header, Form, Button } from 'semantic-ui-react';
import { InputField } from 'react-semantic-redux-form';

import { actionCreators } from './redux';

import { uniqueElementName } from './pipelineUtils';
import { required, minLength2 } from 'lib/reduxFormUtils';

const { pipelineElementAdded } = actionCreators;

const enhance = compose(
  connect(
    (state, props) => ({
      // state
      newElementForm: state.form.newElementName,
      pipeline: state.pipelineEditor.pipelines[props.pipelineId],
      initialValues: props.newElementDefinition
        ? { name: props.newElementDefinition.type }
        : undefined,
    }),
    { pipelineElementAdded },
  ),
  reduxForm({
    form: 'newElementName',
    // We're re-using the same form for each element's modal so we need to permit reinitialization when using the initialValues prop
    enableReinitialize: true,
    touchOnChange: true,
  }),
  // Properties from owner
  withProps(({ // Redux action
    pipelineId, elementId, pipelineElementAdded, newElementDefinition, setNewElementDefinition, newElementForm, reset,
  }) => ({ // from withNewElementDefinition in owner // Redux form
    onConfirmNewElement: () => {
      pipelineElementAdded(pipelineId, elementId, newElementDefinition, newElementForm.values.name);
      setNewElementDefinition(undefined);
      reset();
    },
    onCancelNewElement: () => {
      setNewElementDefinition(undefined);
      reset();
    },
  })),
);

const AddElementModal = ({
  pipeline,
  newElementDefinition,
  setNewElementDefinition,
  invalid,
  submitting,
  onConfirmNewElement,
  onCancelNewElement,
}) => (
  <Modal size="tiny" open={!!newElementDefinition} onClose={onCancelNewElement} dimmer="inverted">
    <Header content="Add New Element" />
    <Modal.Content>
      <Form id="newElementForm">
        <Form.Field>
          <label>Name</label>
          <Field
            name="name"
            component={InputField}
            type="text"
            placeholder="Name"
            validate={[required, minLength2, uniqueElementName(pipeline.pipeline)]}
            autoFocus
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
        form="newElementForm"
      />
      <Button negative content="Cancel" onClick={onCancelNewElement} />
    </Modal.Actions>
  </Modal>
);

AddElementModal.propTypes = {
  pipelineId: PropTypes.string.isRequired,
  elementId: PropTypes.string.isRequired,
  setNewElementDefinition: PropTypes.func.isRequired,
  newElementDefinition: PropTypes.object,
};

export default enhance(AddElementModal);
