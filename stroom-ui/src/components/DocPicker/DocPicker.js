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
import { Input } from 'semantic-ui-react';

import DocRefPropType from 'lib/DocRefPropType';
import { findItem, filterTree } from 'lib/treeUtils';
import { DocRefListingEntryWithBreadcrumb } from 'components/DocRefListingEntry';
import withDocumentTree from 'components/FolderExplorer/withDocumentTree';
import withSelectableItemListing from 'lib/withSelectableItemListing';

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

      return {
        currentFolderWithLineage,
        selectableItemListing,
        documents: documentTreeToUse.children || [],
        onDocRefPickConfirmed,
        selectionNotYetMade:
          selectableItemListing && selectableItemListing.selectedItems.length === 0,
      };
    },
    {},
  ),
  withSelectableItemListing(({ pickerId, documents }) => ({
    listingId: pickerId,
    items: documents,
    openItem: d => console.log('Open item in selectable listing?', d),
  })),
);

const DocPicker = ({
  value,
  listingId,
  setDropdownOpen,
  isDropDownOpen,
  documents,
  onKeyDownWithShortcuts,
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
      icon="search"
      placeholder="Search..."
      value={value}
      onChange={({ target: { value } }) => {
        // searchTermUpdated(value);
        // searchApp({ term: value });
        console.log('Updating value', value);
      }}
    />
    <div className={`dropdown__content ${isDropDownOpen ? 'open' : ''}`}>
      {documents.map((searchResult, index) => (
        <DocRefListingEntryWithBreadcrumb
          key={searchResult.uuid}
          index={index}
          listingId={listingId}
          docRef={searchResult}
          openDocRef={d => console.log('Open Doc Ref?', d)}
        />
      ))}
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
