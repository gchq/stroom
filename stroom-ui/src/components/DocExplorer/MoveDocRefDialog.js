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

import { compose, withProps } from 'recompose';
import { connect } from 'react-redux';

import { Modal, Button, Form } from 'semantic-ui-react';

import { guid } from 'lib/treeUtils';
import { actionCreators } from './redux';
import { moveDocuments } from './explorerClient';

import DocPickerModal from './DocPickerModal';
import PermissionInheritancePicker from 'components/PermissionInheritancePicker';

const { completeDocRefMove } = actionCreators;

const enhance = compose(
  withProps(({ explorerId }) => ({
    explorerId: `move-doc-ref-${explorerId || guid()}`,
  })),
  connect(
    (
      {
        docExplorer: {
          moveDocRef: { isMoving, uuids, destinationUuid },
          explorerTree: { explorers },
        },
        permissionInheritancePicker,
      },
      { explorerId },
    ) => {
      let selectedDestinationUuid;
      const explorer = explorers[explorerId];
      if (explorer && explorer.isSelectedList.length > 0) {
        selectedDestinationUuid = explorer.isSelectedList[0];
      }

      return {
        isMoving,
        uuids,
        permissionInheritance: permissionInheritancePicker[explorerId],
        destinationUuid: selectedDestinationUuid,
      };
    },
    { completeDocRefMove, moveDocuments },
  ),
);

const MoveDocRefDialog = ({
  explorerId,
  isMoving,
  uuids,
  completeDocRefMove,
  moveDocuments,
  destinationUuid,
  permissionInheritance,
}) => (
  <Modal open={isMoving}>
    <Modal.Header>Select a Destination Folder for the Move</Modal.Header>
    <Modal.Content scrolling>
      <Form>
        <Form.Field>
          <label>Destination</label>
          <DocPickerModal pickerId={explorerId} typeFilters={['Folder']} />
        </Form.Field>
        <Form.Field>
          <label>Permission Inheritance</label>
          <PermissionInheritancePicker pickerId={explorerId} />
        </Form.Field>
      </Form>
    </Modal.Content>
    <Modal.Actions>
      <Button negative onClick={completeDocRefMove}>
        Cancel
      </Button>
      <Button
        positive
        onClick={() => moveDocuments(uuids, destinationUuid, permissionInheritance)}
        labelPosition="right"
        icon="checkmark"
        content="Choose"
      />
    </Modal.Actions>
  </Modal>
);

MoveDocRefDialog.propTypes = {
  explorerId: PropTypes.string.isRequired,
};

export default enhance(MoveDocRefDialog);
