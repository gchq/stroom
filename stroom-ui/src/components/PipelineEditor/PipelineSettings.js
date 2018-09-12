import React from 'react';
import PropTypes from 'prop-types';
import { compose, withProps } from 'recompose';
import { connect } from 'react-redux';
import { Field, reduxForm } from 'redux-form';
import { Form, Header } from 'semantic-ui-react';
import { TextAreaField } from 'react-semantic-redux-form';

import Button from 'components/Button';
import ThemedModal from 'components/ThemedModal';
import { actionCreators } from './redux';

const { pipelineSettingsClosed, pipelineSettingsUpdated } = actionCreators;

const enhance = compose(
  connect(
    ({ pipelineEditor: { settings, pipelineStates }, form: { pipelineSettings } }, { pipelineId }) => ({
      ...settings[pipelineId],
      pipelineSettingsForm: pipelineSettings,
      initialValues: {
        description: pipelineStates[pipelineId].pipeline.description,
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
    <ThemedModal
      isOpen={isOpen}
      onClose={onCancel}
      size="tiny"
      dimmer="inverted"
      header={<Header className="header" content="Pipeline Settings" />}
      content={
        <Form>
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
      }
      actions={
        <React.Fragment>
          <Button
            content="Submit"
            disabled={invalid || submitting}
            onClick={onConfirm}
            form="pipelineSettings"
          />
          <Button content="Cancel" onClick={onCancel} />
        </React.Fragment>
      }
    />
  );

PipelineSettings.propTypes = {
  pipelineId: PropTypes.string.isRequired,
};

export default enhance(PipelineSettings);
