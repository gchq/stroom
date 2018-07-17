import React from 'react';
import PropTypes from 'prop-types';
import { compose, withProps } from 'recompose';
import { connect } from 'react-redux';
import { Field, reduxForm } from 'redux-form';

import { Modal, Button, Form, Header } from 'semantic-ui-react';
import { TextAreaField } from 'react-semantic-redux-form';

import { actionCreators } from './redux';

const { pipelineSettingsClosed, pipelineSettingsUpdated } = actionCreators;

const enhance = compose(
  connect(
    ({ pipelineEditor: { settings, pipelines }, form: { pipelineSettings } }, { pipelineId }) => ({
      ...settings[pipelineId],
      pipelineSettingsForm: pipelineSettings,
      initialValues: {
        description: pipelines[pipelineId].pipeline.description,
      },
    }),
    {
      pipelineSettingsClosed,
      pipelineSettingsUpdated,
    },
  ),
  reduxForm({
    form: 'pipelineSettings',
    // We're re-using the same form for each element's modal so we need to permit reinitialization when using the initialValues prop
    enableReinitialize: true,
    touchOnChange: true,
  }),
  withProps(({
    pipelineId,
    pipelineSettingsForm,
    pipelineSettingsUpdated,
    pipelineSettingsClosed,
    reset,
  }) => ({
    onConfirm: () => {
      pipelineSettingsUpdated(pipelineId, pipelineSettingsForm.values.description);
      reset();
    },
    onCancel: () => {
      reset();
      pipelineSettingsClosed(pipelineId);
    },
  })),
);

const PipelineSettings = ({
  pipelineId, onConfirm, onCancel, isOpen, invalid, submitting,
}) => (
  <Modal size="tiny" open={isOpen} onClose={onCancel} dimmer="inverted">
    <Header content="Pipeline Settings" />
    <Modal.Content>
      <Form>
        {/* <Form.Field control={TextArea} placeholder="Describe this pipeline..." /> */}
        <Form.Field>
          <label>Description</label>
          <Field
            name="description"
            component={TextAreaField}
            type="text"
            placeholder="Description"
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
        onClick={onConfirm}
        form="pipelineSettings"
      />
      <Button negative content="Cancel" onClick={onCancel} />
    </Modal.Actions>
  </Modal>
);

PipelineSettings.propTypes = {
  pipelineId: PropTypes.string.isRequired,
};

export default enhance(PipelineSettings);
