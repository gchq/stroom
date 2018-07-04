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

import { compose, withState, lifecycle, branch, renderComponent } from 'recompose';
import { connect } from 'react-redux';

import { Button, Modal, Input, Loader } from 'semantic-ui-react';

import { findItem } from 'lib/treeUtils';
import { actionCreators } from './redux';

import withExplorerTree from './withExplorerTree';
import withDocRefTypes from './withDocRefTypes';
import DocExplorer from './DocExplorer';

const { docRefPicked, explorerTreeOpened } = actionCreators;

const withModal = withState('isOpen', 'setIsOpen', false);

const enhance = compose(
  withExplorerTree,
  withDocRefTypes,
  connect(
    (state, props) => ({
      documentTree: state.explorerTree.documentTree,
      docRef: state.explorerTree.pickedDocRefs[props.pickerId],
      explorer: state.explorerTree.explorers[props.pickerId],
    }),
    {
      // actions
      docRefPicked,
      explorerTreeOpened,
    },
  ),
  lifecycle({
    componentDidMount() {
      const { explorerTreeOpened, pickerId, typeFilter } = this.props;
      explorerTreeOpened(pickerId, false, false, typeFilter);
    },
  }),
  withModal,
  branch(
    ({ explorer }) => !explorer,
    renderComponent(() => <Loader active>Loading Explorer</Loader>),
  ),
);

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
      dimmer="inverted"
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
        <Button negative onClick={handleClose}>
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
  typeFilter: PropTypes.string,
};

export default enhance(DocRefModalPicker);
