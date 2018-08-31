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

import { compose, withState, branch, renderComponent, withHandlers } from 'recompose';
import { connect } from 'react-redux';
import { Input, Button, Loader, Icon } from 'semantic-ui-react';

import DocRefPropType from 'lib/DocRefPropType';
import { findItem, filterTree } from 'lib/treeUtils';
import DocRefListingEntry from 'components/DocRefListingEntry';
import withDocumentTree from 'components/FolderExplorer/withDocumentTree';
import withSelectableItemListing from 'lib/withSelectableItemListing';
import { SELECTION_BEHAVIOUR } from 'lib/withSelectableItemListing/redux';

const withDropdownOpen = withState('isDropDownOpen', 'setDropdownOpen', false);
const withFolderUuid = withState('folderUuid', 'setFolderUuid', undefined);

const enhance = compose(
  withDropdownOpen,
  withFolderUuid,
  withDocumentTree,
  connect(
    (
      { folderExplorer: { documentTree }, selectableItemListings },
      {
        pickerId, onChange, setModalIsOpen, folderUuid, typeFilters,
      },
    ) => {
      const documentTreeToUse =
        typeFilters.length > 0
          ? filterTree(documentTree, d => typeFilters.includes(d.type))
          : documentTree;
      const folderUuidToUse = folderUuid || documentTreeToUse.uuid;

      const selectableItemListing = selectableItemListings[pickerId];
      const currentFolderWithLineage = findItem(documentTreeToUse, folderUuidToUse);

      const onDocRefPickConfirmed = () => {
        onChange(selectableItemListing.selectedItems[0]);
        setModalIsOpen(false);
      };
      const parentFolder = (currentFolderWithLineage.lineage &&
      currentFolderWithLineage.lineage.length > 0) ? currentFolderWithLineage.lineage[currentFolderWithLineage.lineage.length - 1] : undefined;

      return {
        currentFolderWithLineage,
        selectableItemListing,
        onDocRefPickConfirmed,
        parentFolder,
        selectionNotYetMade:
          selectableItemListing && selectableItemListing.selectedItems.length === 0,
      };
    },
    {},
  ), 
  withHandlers({
    enterFolder: ({setFolderUuid, parentFolder}) => d => setFolderUuid(d.uuid),
    goBackToParentFolder: ({setFolderUuid, parentFolder}) => () => {if (parentFolder) {setFolderUuid(parentFolder.uuid)}}
  }),
  branch(
    ({ currentFolderWithLineage }) => !(currentFolderWithLineage && currentFolderWithLineage.node),
    renderComponent(() => <Loader active>Loading data</Loader>),
  ),
  withSelectableItemListing(({ pickerId, currentFolderWithLineage, enterFolder, goBackToParentFolder }) => ({
    listingId: pickerId,
    items: currentFolderWithLineage.node.children,
    openItem: d => console.log('Open item in selectable listing?', d),
    enterItem: enterFolder,
    goBack: goBackToParentFolder,
    selectionBehaviour: SELECTION_BEHAVIOUR.SINGLE,
  })),
);

const DocPicker = ({
  value,
  listingId,
  setDropdownOpen,
  isDropDownOpen,
  currentFolderWithLineage,
  onKeyDownWithShortcuts,
  setFolderUuid,
  parentFolder,
  enterFolder,
  goBackToParentFolder
}) => (
  <div
    className="dropdown"
    tabIndex={0}
    onFocus={() => setDropdownOpen(true)}
    onBlur={() => setDropdownOpen(false)}
    onKeyDown={onKeyDownWithShortcuts}
  >
    <Input
      fluid
      tabIndex={-1}
      className="border flat"
      placeholder="Choose..."
      value={value}
      onChange={({ target: { value } }) => {
        // searchTermUpdated(value);
        // searchApp({ term: value });
        console.log('Updating value', value);
      }}
    />
    <div className={`dropdown__content ${isDropDownOpen ? 'open' : ''}`}>
      <div className="app-search-header">
        {parentFolder && (
            <Icon
              name="arrow left"
              size='large'
              onClick={goBackToParentFolder}
            />
          )}
        {currentFolderWithLineage.node.name}
      </div>
      <div className="app-search-listing">
        {currentFolderWithLineage.node.children && currentFolderWithLineage.node.children.map((searchResult, index) => (
          <DocRefListingEntry
            key={searchResult.uuid}
            index={index}
            listingId={listingId}
            docRef={searchResult}
            openDocRef={d => console.log('Open Doc Ref?', d)}
            enterFolder={enterFolder}
          />
        ))}
      </div>
      <div className="app-search-footer">
        <Button primary>Choose</Button>
      </div>
    </div>
  </div>
);

const EnhancedDocPickerModal = enhance(DocPicker);

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
