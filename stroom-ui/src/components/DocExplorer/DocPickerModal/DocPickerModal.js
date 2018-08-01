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

const {
  docExplorerOpened, searchTermUpdated, folderOpenToggled, docRefSelected,
} = actionCreators;

const withModal = withState('isOpen', 'setIsOpen', false);

const enhance = compose(
  withExplorerTree,
  withDocRefTypes,
  connect(
    (
      {
        docExplorer: {
          explorerTree: { documentTree, explorers },
        },
      },
      { explorerId, value },
    ) => ({
      documentTree,
      explorer: explorers[explorerId],
    }),
    {
      docExplorerOpened,
      searchTermUpdated,
      folderOpenToggled,
      docRefSelected,
    },
  ),
  branch(
    ({ documentTree }) => !documentTree,
    renderComponent(() => <Loader active>Awaiting Document Tree</Loader>),
  ),
  lifecycle({
    componentDidMount() {
      const { docExplorerOpened, explorerId, typeFilters } = this.props;
      docExplorerOpened(explorerId, false, typeFilters);
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
  searchTermUpdated,
  isOpen,
  explorerId,
  typeFilters,
  setIsOpen,
  explorer,
  onChange,
  value,
  folderOpenToggled,
  docRefSelected,
}) => {
  const handleOpen = () => setIsOpen(true);

  const handleClose = () => setIsOpen(false);

  const onDocRefPickConfirmed = () => {
    const result = findItem(documentTree, explorer.isSelected);
    onChange(result.node);

    handleClose();
  };

  let trigger;
  if (value) {
    const { lineage, node } = findItem(documentTree, value.uuid);
    const triggerValue = `${lineage.map(d => d.name).join(' > ')}${
      lineage.length > 0 ? ' > ' : ''
    }${node.name}`;
    trigger = (
      <Dropdown
        // it moans about mixing trigger and selection, but it's the only way to make it look right..?
        selection
        fluid
        onFocus={handleOpen}
        trigger={
          <span>
            <img className="doc-ref__icon" alt="X" src={require(`../images/${node.type}.svg`)} />
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
          onChange={e => searchTermUpdated(explorerId, e.target.value)}
        />
        <FolderToPick
          explorerId={explorerId}
          explorer={explorer}
          folder={documentTree}
          typeFilters={typeFilters}
          folderOpenToggled={folderOpenToggled}
          docRefSelected={docRefSelected}
        />
      </Modal.Content>
      <Modal.Actions>
        <Button negative onClick={handleClose}>
          Cancel
        </Button>
        <Button
          positive
          onClick={onDocRefPickConfirmed}
          labelPosition="right"
          disabled={!explorer.isSelected}
          icon="checkmark"
          content="Choose"
        />
      </Modal.Actions>
    </Modal>
  );
};

const EnhancedDocPickerModal = enhance(DocPickerModal);

const docRefShape = {
  type: PropTypes.string.isRequired,
  uuid: PropTypes.string.isRequired,
  name: PropTypes.string.isRequired,
};

EnhancedDocPickerModal.propTypes = {
  explorerId: PropTypes.string.isRequired,
  typeFilters: PropTypes.array.isRequired,
  onChange: PropTypes.func.isRequired,
  value: PropTypes.shape({
    node: PropTypes.shape(docRefShape),
    lineage: PropTypes.arrayOf(PropTypes.shape(docRefShape)),
  }),
};

EnhancedDocPickerModal.defaultProps = {
  typeFilters: [],
  onChange: v => console.log('Not implemented onChange, value ignored', v),
};

export default EnhancedDocPickerModal;
