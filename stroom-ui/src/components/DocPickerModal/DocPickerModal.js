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

import { Button, Modal, Input, Popup } from 'semantic-ui-react';

import DocRefPropType from 'lib/DocRefPropType';
import withExplorerTree from 'components/FolderExplorer/withExplorerTree';
import { findItem, iterateNodes } from 'lib/treeUtils';
import DocRefListing from 'components/DocRefListing';

const withModal = withState('modalIsOpen', 'setModalIsOpen', false);

const enhance = compose(
  withExplorerTree,
  withModal,
  connect(
    (
      { folderExplorer: { documentTree }, docRefListing },
      {
        pickerId, typeFilters, onChange, setModalIsOpen,
      },
    ) => {
      let allDocuments = [];
      let thisDocRefListing = docRefListing[pickerId];
      
      iterateNodes(documentTree, (lineage, node) => {
        allDocuments.push({
          name: node.name,
          type: node.type,
          uuid: node.uuid,
          lineage,
          lineageNames: lineage.reduce((acc, curr) => `${acc} ${curr.name}`, ''),
        });
      });

      if (typeFilters.length > 0) {
        allDocuments = allDocuments.filter(n => typeFilters.includes(n.type));
      }

      const onDocRefPickConfirmed = () => {
        const result = findItem(documentTree, thisDocRefListing.selectedDocRefUuids[0]);
        onChange(result.node);
        setModalIsOpen(false);
      };

      return {
        allDocuments,
        docRefListing: thisDocRefListing,
        documentTree,
        onDocRefPickConfirmed,
        selectionNotYetMade: thisDocRefListing && thisDocRefListing.selectedDocRefUuids.length === 0,
      };
    },
    {},
  ),
);

const DocPickerModal = ({
  modalIsOpen,
  setModalIsOpen,

  documentTree,
  onDocRefPickConfirmed,
  selectionNotYetMade,
  allDocuments,
  pickerId,
  typeFilters,
  value,
}) => {
  let trigger;
  console.log('Value?', value);
  if (value && value.uuid) {
    const { lineage, node } = findItem(documentTree, value.uuid);
    const triggerValue = `${lineage.map(d => d.name).join(' > ')}${
      lineage.length > 0 ? ' > ' : ''
    }${node.name}`;
    trigger = (
      <Popup
        trigger={
          <Button
            // it moans about mixing trigger and selection, but it's the only way to make it look right..?
            onFocus={() => setModalIsOpen(true)}
            basic
          >
            <img
              className="stroom-icon--small"
              alt="X"
              src={require(`../../images/docRefTypes/${node.type}.svg`)}
            />
            {value.name}
          </Button>
        }
      >
        {triggerValue}
      </Popup>
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
          maxResults={5}
          icon="search"
          title="Search"
          allDocRefs={allDocuments}
          fixedDocRefTypeFilters={typeFilters}
          openDocRef={d => console.log('Open Doc Ref?', d)}
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
          disabled={selectionNotYetMade}
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
  value: DocRefPropType,
};

EnhancedDocPickerModal.defaultProps = {
  typeFilters: [],
  onChange: v => console.log('Not implemented onChange, value ignored', v),
};

export default EnhancedDocPickerModal;
