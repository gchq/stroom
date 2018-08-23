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
import { findItem, filterTree } from 'lib/treeUtils';
// import DocRefListingEntry from 'components/DocRefListingEntry';
import withDocumentTree from 'components/FolderExplorer/withDocumentTree';

const withModal = withState('modalIsOpen', 'setModalIsOpen', false);
const withFolderUuid = withState('folderUuid', 'setFolderUuid', undefined);

const enhance = compose(
  withModal,
  withFolderUuid,
  withDocumentTree,
  connect(
    (
      { folderExplorer: { documentTree }, docRefListing },
      {
        pickerId, onChange, setModalIsOpen, folderUuid, typeFilters,
      },
    ) => {
      const documentTreeToUse =
        typeFilters.length > 0
          ? filterTree(documentTree, d => typeFilters.includes(d.type))
          : documentTree;
      const folderUuidToUse = folderUuid || documentTree.uuid;

      const thisDocRefListing = docRefListing[pickerId];
      const currentFolderWithLineage = findItem(documentTreeToUse, folderUuidToUse);

      const onDocRefPickConfirmed = () => {
        const result = findItem(documentTreeToUse, thisDocRefListing.selectedDocRefUuids[0]);
        onChange(result.node);
        setModalIsOpen(false);
      };

      return {
        currentFolderWithLineage,
        docRefListing: thisDocRefListing,
        documentTree: documentTreeToUse,
        onDocRefPickConfirmed,
        selectionNotYetMade:
          thisDocRefListing && thisDocRefListing.selectedDocRefUuids.length === 0,
      };
    },
    {},
  ),
);

const DocPickerModal = ({
  modalIsOpen,
  setModalIsOpen,
  setFolderUuid,

  currentFolderWithLineage,
  documentTree,
  onDocRefPickConfirmed,
  selectionNotYetMade,
  pickerId,
  value,
}) => {
  let trigger;

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
        <Grid className="content-tabs__grid">
          <Grid.Column width={16}>
            <AppSearchBar />
          </Grid.Column>
          <Grid.Column width={16}>
            <Header as="h3">
              <Icon name={icon} />
              <Header.Content>{title}</Header.Content>
              {parentFolder && (
                <Header.Subheader>
                  <DocRefBreadcrumb docRefUuid={parentFolder.uuid} openDocRef={openDocRef} />
                </Header.Subheader>
              )}
            </Header>
          </Grid.Column>
        </Grid>
        <div className="doc-ref-listing">
          {docRefs.map((docRef, index) => (
            <DndDocRefListingEntry
              key={docRef.uuid}
              index={index}
              listingId={listingId}
              docRefUuid={docRef.uuid}
              includeBreadcrumb={includeBreadcrumbOnEntries}
              onNameClick={node => openDocRef(node)}
              openDocRef={openDocRef}
            />
          ))}
        </div>
        {/* <DocRefListingEntry
          listingId={pickerId}
          icon="search"
          title="Search"
          includeBreadcrumbOnEntries={false}
          parentFolder={currentFolderWithLineage.node}
          items={currentFolderWithLineage.node.children}
          openDocRef={d => {
            // This will open a folder even if children are hidden by filtering...
            if (d.children && d.children.length > 0) {
              setFolderUuid(d.uuid);
            }
          }}
        /> */}
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
