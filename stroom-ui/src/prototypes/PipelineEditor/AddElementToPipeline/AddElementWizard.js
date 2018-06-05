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

import { compose } from 'recompose';
import { connect } from 'react-redux';
import { Field, reduxForm } from 'redux-form';

import { Modal, Header, Button, Form, Icon } from 'semantic-ui-react';

import {
  cancelAddPipelineElement,
  restartAddPipelineElement,
  ADD_ELEMENT_STATE,
} from './redux';
import { pipelineElementAdded } from '../redux';
import { withAddElementToPipeline } from './withAddElementToPipeline';

import ChooseElement from './ChooseElement';

const AddElementWizard = ({
  addElementToPipelineWizard,
  cancelAddPipelineElement,
  restartAddPipelineElement,
  availableElements,
  pipelineElementAdded,
  handleSubmit,
}) => {
  let stage;
  let headerContent;
  let goBackButton;
  let submitButton;

  const submitName = (values) => {
    pipelineElementAdded(
      addElementToPipelineWizard.pipelineId,
      addElementToPipelineWizard.parentId,
      addElementToPipelineWizard.childDefinition,
      values.name,
    );
  };

  switch (addElementToPipelineWizard.addElementState) {
    case ADD_ELEMENT_STATE.PICKING_ELEMENT_DEFINITION:
      stage = <ChooseElement availableElements={availableElements} />;
      headerContent = `Choose an Element to Add to ${addElementToPipelineWizard.parentId}`;
      break;
    case ADD_ELEMENT_STATE.PICKING_NAME:
      stage = (
        <Form>
          <Form.Field>
            <label>Name</label>
            <Field name="name" component="input" type="text" placeholder="Name" />
          </Form.Field>
        </Form>
      );
      headerContent = `Give the new ${addElementToPipelineWizard.childDefinition.type} a name`;
      goBackButton = (
        <Button icon labelPosition="left" onClick={restartAddPipelineElement}>
          <Icon name="arrow left" />
          Change type
        </Button>
      );
      submitButton = <Button positive content="Submit" onClick={handleSubmit(submitName)} />;
      break;
    default:
      headerContent = 'Invalid picking state for new element';
  }

  return (
    <Modal
      size="tiny"
      open={addElementToPipelineWizard.addElementState !== ADD_ELEMENT_STATE.NOT_ADDING}
      onClose={cancelAddPipelineElement}
      closeOnRootNodeClick={false}
    >
      <Header content={headerContent} />
      <Modal.Content>
        <div>{stage}</div>
      </Modal.Content>
      <Modal.Actions>
        {goBackButton}
        {submitButton}
        <Button negative content="Cancel" onClick={cancelAddPipelineElement} />
      </Modal.Actions>
    </Modal>
  );
};

AddElementWizard.propTypes = {
  addElementToPipelineWizard: PropTypes.object.isRequired,
  availableElements: PropTypes.object,
  cancelAddPipelineElement: PropTypes.func.isRequired,
  pipelineElementAdded: PropTypes.func.isRequired,
};

export default compose(
  connect(
    state => ({
      // state
    }),
    {
      // actions
      pipelineElementAdded,
      cancelAddPipelineElement,
      restartAddPipelineElement,
    },
  ),
  withAddElementToPipeline(),
  reduxForm({ form: 'addElementToPipeline' }),
)(AddElementWizard);
