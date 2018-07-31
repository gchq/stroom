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

import { Button, Modal, Input, Loader, Dropdown } from 'semantic-ui-react';

import { findItem } from 'lib/treeUtils';
import { actionCreators } from '../redux';

import withExplorerTree from '../withExplorerTree';
import withDocRefTypes from '../withDocRefTypes';
import FolderToPick from './FolderToPick';

const { docRefPicked, docExplorerOpened } = actionCreators;

const withModal = withState('isOpen', 'setIsOpen', false);

const enhance = compose(
  withExplorerTree,
  withDocRefTypes,
  connect(
    (
      {
        docExplorer: {
          explorerTree: { documentTree, explorers },
          docRefPicker,
        },
      },
      { pickerId },
    ) => ({
      documentTree,
      docRefWithLineage: docRefPicker[pickerId],
      explorer: explorers[pickerId],
    }),
    {
      // actions
      docRefPicked,
      docExplorerOpened,
    },
  ),
  branch(
    ({ documentTree }) => !documentTree,
    renderComponent(() => <Loader active>Awaiting Document Tree</Loader>),
  ),
  lifecycle({
    componentDidMount() {
      const { docExplorerOpened, pickerId, typeFilters } = this.props;
      docExplorerOpened(pickerId, false, typeFilters);
    },
  }),
  withModal,
  branch(
    ({ explorer }) => !explorer,
    renderComponent(() => <Loader active>Loading Explorer</Loader>),
  ),
);

const DocPickerModal = ({
  isSelected,
  documentTree,
  docRefPicked,
  docRefWithLineage,
  isOpen,
  pickerId,
  typeFilters,
  setIsOpen,
  explorer,
  onChange,
}) => {
  const handleOpen = () => setIsOpen(true);

  const handleClose = () => setIsOpen(false);

  const onDocRefPickConfirmed = () => {
    Object.keys(explorer.isSelected).forEach((pickedUuid) => {
      const { node, lineage } = findItem(documentTree, pickedUuid);
      docRefPicked(pickerId, node, lineage);
      onChange({ node, lineage });
    });

    handleClose();
  };

  let trigger;
  if (docRefWithLineage) {
    const triggerValue = `${docRefWithLineage.lineage.map(d => d.name).join(' > ')} > ${
      docRefWithLineage.docRef.name
    }`;
    trigger = (
      <Dropdown
        // it moans about mixing trigger and selection, but it's the only way to make it look right..?
        selection
        fluid
        onFocus={handleOpen}
        trigger={
          <span>
            <img
              className="doc-ref__icon"
              alt="X"
              src={require(`../images/${docRefWithLineage.docRef.type}.svg`)}
            />
            {triggerValue}
          </span>
        }
      />
    );
  } else {
    trigger = <Input fluid onFocus={handleOpen} value="..." />;
  }

  return (
    <Modal trigger={trigger} open={isOpen} onClose={handleClose} size="small" dimmer="inverted">
      <Modal.Header>Select a Doc Ref</Modal.Header>
      <Modal.Content scrolling>
        <Input
          icon="search"
          placeholder="Search..."
          value={explorer.searchTerm}
          onChange={e => searchTermUpdated(pickerId, e.target.value)}
        />
        <FolderToPick pickerId={pickerId} folder={documentTree} typeFilters={typeFilters} />
      </Modal.Content>
      <Modal.Actions>
        <Button negative onClick={handleClose}>
          Cancel
        </Button>
        <Button
          positive
          onClick={onDocRefPickConfirmed}
          labelPosition="right"
          icon="checkmark"
          content="Choose"
        />
      </Modal.Actions>
    </Modal>
  );
};

const EnhancedDocPickerModal = enhance(DocPickerModal);

EnhancedDocPickerModal.propTypes = {
  pickerId: PropTypes.string.isRequired,
  typeFilters: PropTypes.array.isRequired,
  onChange: PropTypes.func,
};

EnhancedDocPickerModal.defaultProps = {
  typeFilters: [],
  onChange: d => console.log('On Change Not Implemented, Falling back to Default', d),
};

export default EnhancedDocPickerModal;
