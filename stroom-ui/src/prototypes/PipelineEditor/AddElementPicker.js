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
import React, { Component } from 'react';
import PropTypes from 'prop-types';

import { compose } from 'redux';
import { connect } from 'react-redux';

import { Dropdown, Menu, Icon, Label, Input, Modal, Header } from 'semantic-ui-react';

import {
  cancelAddPipelineElement,
  choosePipelineElementToAdd,
  addElementSearchTermChanged,
} from './redux';
import { withAddElementToPipeline } from './withAddElementToPipeline';
import { withPipeline } from './withPipeline';
import { withElement } from './withElement';

const AddElementPicker = ({
  pipelineId,
  pipeline,
  pendingElementToAddParent,
  pendingElementToAddChildDefinition,
  addElementSearchTermChanged,
  choosePipelineElementToAdd,
  cancelAddPipelineElement,
  element,
  searchTerm,
  availableElements,
}) => (
  <Modal open={!!element} onClose={() => cancelAddPipelineElement(pipelineId)}>
    <Header icon="browser" content={`Choose an Element to Add to ${pendingElementToAddParent}`} />
    <Modal.Content>
      <Menu vertical>
        <Menu.Item>
          <Input
            className="icon"
            icon="search"
            placeholder="Search..."
            value={searchTerm}
            onChange={e => addElementSearchTermChanged(pipelineId, e.target.value)}
          />
        </Menu.Item>
        {Object.entries(availableElements).map(k => (
          <Menu.Item key={k[0]}>
            {k[0]}
            <Menu.Menu>
              {k[1]
                  .filter(k => (!searchTerm && searchTerm.length === 0) || k.type.includes(searchTerm))
                  .map(e => (
                    <Menu.Item
                      key={e.type}
                      name={e.type}
                      onClick={() => choosePipelineElementToAdd(pipelineId, e)}
                    />
                  ))}
            </Menu.Menu>
          </Menu.Item>
          ))}
      </Menu>
    </Modal.Content>
  </Modal>
);

AddElementPicker.propTypes = {
  pipelineId: PropTypes.string.isRequired,
  pipeline: PropTypes.object.isRequired,
  element: PropTypes.object.isRequired,
  searchTerm: PropTypes.string.isRequired,
  pendingElementToAddParent: PropTypes.string,
  availableElements: PropTypes.object,
  cancelAddPipelineElement: PropTypes.func.isRequired,
  choosePipelineElementToAdd: PropTypes.func.isRequired,
};

export default compose(
  connect(
    state => ({
      // state
    }),
    {
      // actions
      choosePipelineElementToAdd,
      cancelAddPipelineElement,
      addElementSearchTermChanged,
    },
  ),
  withPipeline(),
  withAddElementToPipeline(),
  withElement('pendingElementToAddParent'),
)(AddElementPicker);
