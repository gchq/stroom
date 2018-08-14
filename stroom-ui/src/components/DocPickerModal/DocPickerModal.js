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

import { compose, withState, withProps } from 'recompose';
import { connect } from 'react-redux';

import { Button, Modal, Input, Popup, Dropdown } from 'semantic-ui-react/dist/commonjs';

import DocRefPropType from 'lib/DocRefPropType';
import { findItem } from 'lib/treeUtils';

import DocRefListing from 'components/DocRefListing';

const withModal = withState('modalIsOpen', 'setModalIsOpen', false);

const enhance = compose(
  connect(
    ({ docExplorer: { documentTree } }, { pickerId, value }) => ({
      documentTree,
    }),
    {},
  ),
  withModal,
);

const DocPickerModal = ({
  modalIsOpen,
  setModalIsOpen,

  documentTree,
  pickerId,
  typeFilters,
  onChange,
  value,
}) => {
  const onDocRefPickConfirmed = () => {
    console.log('Do something, something has been picked');
    // const result = findItem(documentTree, explorer.isSelected);
    // onChange(result.node);
    setModalIsOpen(false);
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
        onFocus={() => setModalIsOpen(true)}
        trigger={
          <span>
            <img
              className="stroom-icon--small"
              alt="X"
              src={require(`../../images/docRefTypes/${node.type}.svg`)}
            />
            {triggerValue}
          </span>
        }
      />
    );
  } else {
    trigger = <Input onFocus={() => setModalIsOpen(true)} value="..." />;
  }
  return (
    <Modal
      trigger={trigger}
      open={modalIsOpen}
      onClose={() => setModalIsOpen(false)}
      size="small"
      dimmer="inverted"
    >
      <Modal.Content scrolling>
        <DocRefListing
          listingId={pickerId}
          icon="folder"
          title="Find stuff"
          parentFolder={documentTree}
          docRefs={documentTree.children}
          includeBreadcrumbOnEntries={false}
          fixedDocRefTypeFilters={typeFilters}
        />
      </Modal.Content>
      <Modal.Actions>
        <Button negative onClick={() => setModalIsOpen(false)}>
          Cancel
        </Button>
        <Button
          positive
          onClick={onDocRefPickConfirmed}
          labelPosition="right"
          disabled={
            false // come back to this
          }
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
  onChange: PropTypes.func.isRequired,
  value: PropTypes.shape({
    node: PropTypes.shape(DocRefPropType),
    lineage: PropTypes.arrayOf(PropTypes.shape(DocRefPropType)),
  }),
};

EnhancedDocPickerModal.defaultProps = {
  typeFilters: [],
  onChange: v => console.log('Not implemented onChange, value ignored', v),
};

export default EnhancedDocPickerModal;
