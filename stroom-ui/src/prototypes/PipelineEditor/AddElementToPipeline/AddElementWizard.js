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

import { compose } from 'redux';
import { connect } from 'react-redux';

import { Dropdown, Menu, Icon, Label, Input, Modal, Header } from 'semantic-ui-react';

import {
  cancelAddPipelineElement,
  choosePipelineElementToAdd,
  addElementSearchTermChanged,
  ADD_ELEMENT_STATE,
} from './redux';
import { pipelineElementAdded } from '../redux';
import { withAddElementToPipeline } from './withAddElementToPipeline';

import ChooseElement from './ChooseElement';
import NameNewElement from './NameNewElement';

const AddElementWizard = ({
  addElementToPipelineWizard,
  cancelAddPipelineElement,
  availableElements,
  pipelineElementAdded,
}) => {
  let stage;
  let headerContent;

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
      stage = <NameNewElement onSubmit={submitName} />;
      headerContent = `Give the new ${addElementToPipelineWizard.childDefinition.type} a name`;
      break;
  }

  return (
    <Modal
      open={addElementToPipelineWizard.addElementState !== ADD_ELEMENT_STATE.NOT_ADDING}
      onClose={() => cancelAddPipelineElement()}
    >
      <Header icon="browser" content={headerContent} />
      <Modal.Content>
        <div>{stage}</div>
      </Modal.Content>
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
    },
  ),
  withAddElementToPipeline(),
)(AddElementWizard);
