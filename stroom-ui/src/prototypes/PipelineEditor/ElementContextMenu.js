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

import { withState } from 'recompose';
import { Confirm } from 'semantic-ui-react';

import { compose } from 'recompose';
import { connect } from 'react-redux';

import { Dropdown } from 'semantic-ui-react';

import { actionCreators } from './redux';
import { actionCreators as addElementActionCreators } from './AddElementToPipeline';

const { pipelineElementDeleted } = actionCreators;

const { initiateAddPipelineElement } = addElementActionCreators;

const withPendingDeletion = withState('pendingDeletion', 'setPendingDeletion', false);

const ElementContextMenu = ({
  pipelineId,
  elementId,
  isOpen,
  closeContextMenu,
  initiateAddPipelineElement,
  pipelineElementDeleted,

  // withPendingDeletion
  pendingDeletion,
  setPendingDeletion,
}) => (
  <div>
    <Confirm
      open={!!pendingDeletion}
      content="This will delete the element from the pipeline, are you sure?"
      onCancel={() => setPendingDeletion(false)}
      onConfirm={() => {
        pipelineElementDeleted(pipelineId, pendingDeletion);
        setPendingDeletion(false);
      }}
    />
    <Dropdown floating direction="right" icon={null} open={isOpen} onClose={closeContextMenu}>
      <Dropdown.Menu>
        <Dropdown.Item
          icon="add"
          text="Add"
          onClick={() => initiateAddPipelineElement(pipelineId, elementId)}
        />
        <Dropdown.Item icon="trash" text="Delete" onClick={() => setPendingDeletion(elementId)} />
      </Dropdown.Menu>
    </Dropdown>
  </div>
);

ElementContextMenu.propTypes = {
  pipelineId: PropTypes.string.isRequired,
  elementId: PropTypes.string.isRequired,
  isOpen: PropTypes.bool.isRequired,
  closeContextMenu: PropTypes.func.isRequired,

  pipelineElementDeleted: PropTypes.func.isRequired,
  initiateAddPipelineElement: PropTypes.func.isRequired,

  // withPendingDeletion
  pendingDeletion: PropTypes.bool.isRequired,
  setPendingDeletion: PropTypes.func.isRequired,
};

export default compose(
  connect(
    state => ({
      // state
    }),
    {
      initiateAddPipelineElement,
      pipelineElementDeleted,
    },
  ),
  withPendingDeletion,
)(ElementContextMenu);
