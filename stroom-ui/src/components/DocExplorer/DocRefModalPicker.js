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

import { compose, withState } from 'recompose';
import { connect } from 'react-redux';

import { Button, Modal, Input } from 'semantic-ui-react';

import { findItem } from 'lib/treeUtils';
import { actionCreators } from './redux';
import { withCreatedExplorer } from './withExplorer';
import { withPickedDocRef } from './withPickedDocRef';

import DocExplorer from './DocExplorer';

const { docRefPicked } = actionCreators;

const withModal = withState('isOpen', 'setIsOpen', false);

const DocRefModalPicker = ({
  isSelected,
  documentTree,
  docRefPicked,
  docRef,
  isOpen,
  tree,
  pickerId,
  typeFilter,
  setIsOpen,
  explorer,
}) => {
  const value = docRef ? docRef.name : '';

  const handleOpen = () => setIsOpen(true);

  const handleClose = () => setIsOpen(false);

  const onDocRefSelected = () => {
    Object.keys(explorer.isSelected).forEach((pickedUuid) => {
      const picked = findItem(documentTree, pickedUuid);
      docRefPicked(pickerId, picked);
    });

    handleClose();
  };

  return (
    <Modal
      trigger={<Input onFocus={handleOpen} value={`${value}...`} />}
      open={isOpen}
      onClose={handleClose}
      size="small"
      dimmer="blurring"
    >
      <Modal.Header>Select a Doc Ref</Modal.Header>
      <Modal.Content scrolling>
        <DocExplorer
          tree={tree}
          explorerId={pickerId}
          allowMultiSelect={false}
          allowDragAndDrop={false}
          typeFilter={typeFilter}
        />
      </Modal.Content>
      <Modal.Actions>
        <Button negative onClick={() => setIsOpen(false)}>
          Cancel
        </Button>
        <Button
          positive
          onClick={onDocRefSelected}
          labelPosition="right"
          icon="checkmark"
          content="Choose"
        />
      </Modal.Actions>
    </Modal>
  );
};

DocRefModalPicker.propTypes = {
  pickerId: PropTypes.string.isRequired,
  documentTree: PropTypes.object.isRequired,
  explorer: PropTypes.object.isRequired,

  typeFilter: PropTypes.string,
  docRef: PropTypes.object,
  docRefPicked: PropTypes.func.isRequired,
  isOpen: PropTypes.bool.isRequired,

  setIsOpen: PropTypes.func.isRequired,
};

export default compose(
  connect(
    state => ({
      documentTree: state.explorerTree.documentTree,
    }),
    {
      // actions
      docRefPicked,
    },
  ),
  withPickedDocRef(),
  withCreatedExplorer('pickerId'),
  withModal,
)(DocRefModalPicker);
