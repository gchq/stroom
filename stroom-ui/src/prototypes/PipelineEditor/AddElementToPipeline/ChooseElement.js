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

import { connect } from 'react-redux';

import { Menu, Input } from 'semantic-ui-react';

import {
  cancelAddPipelineElement,
  choosePipelineElementToAdd,
  addElementSearchTermChanged,
} from './redux';

const ChooseElement = ({
  addElementToPipelineWizard,
  addElementSearchTermChanged,
  choosePipelineElementToAdd,
  availableElements,
}) => (
  <Menu vertical>
    <Menu.Item>
      <Input
        className="icon"
        icon="search"
        placeholder="Search..."
        value={addElementToPipelineWizard.searchTerm}
        onChange={e => addElementSearchTermChanged(e.target.value)}
      />
    </Menu.Item>
    {Object.entries(availableElements).map(k => (
      <Menu.Item key={k[0]}>
        {k[0]}
        <Menu.Menu>
          {k[1].map(e => (
            <Menu.Item key={e.type} name={e.type} onClick={() => choosePipelineElementToAdd(e)} />
          ))}
        </Menu.Menu>
      </Menu.Item>
    ))}
  </Menu>
);

ChooseElement.propTypes = {
  addElementToPipelineWizard: PropTypes.object.isRequired,
  availableElements: PropTypes.object.isRequired,
  choosePipelineElementToAdd: PropTypes.func.isRequired,
  addElementSearchTermChanged: PropTypes.func.isRequired,
};

export default connect(
  state => ({
    addElementToPipelineWizard: state.addElementToPipelineWizard,
  }),
  {
    cancelAddPipelineElement,
    choosePipelineElementToAdd,
    addElementSearchTermChanged,
  },
)(ChooseElement);
