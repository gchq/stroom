import React from 'react';
import PropTypes from 'prop-types';

import { compose, withState, withProps } from 'recompose';
import { connect } from 'react-redux';
import { Input, Icon, Button } from 'semantic-ui-react';

import { findItem } from 'lib/treeUtils';
import { actionCreators as appSearchBarActionCreators, defaultPickerState, SEARCH_MODE } from './redux';
import { searchApp } from 'components/FolderExplorer/explorerClient';
import { DocRefBreadcrumb } from 'components/DocRefBreadcrumb';
import DocRefListingEntry from 'components/DocRefListingEntry';
import { withDocRefTypes } from 'components/DocRefTypes';
import withSelectableItemListing from 'lib/withSelectableItemListing';
import withDocumentTree from 'components/FolderExplorer/withDocumentTree';

const { searchTermUpdated, navigateToFolder } = appSearchBarActionCreators;

const withDropdownOpen = withState('isDropDownOpen', 'setDropdownOpen', false);

const enhance = compose(
  withDocRefTypes,
  withDropdownOpen,
  withDocumentTree,
  connect(
    ({ appSearch, folderExplorer: { documentTree } }, { pickerId }) => {
      const appSearchForPicker = appSearch[pickerId] || defaultPickerState;
      const { searchTerm, navFolder, searchResults, searchMode } = appSearchForPicker;

      let docRefs = searchResults;
      let thisFolder;
      let parentFolder;
      if (searchMode === SEARCH_MODE.NAVIGATION) {
        const navFolderToUse = navFolder || documentTree;
        const navFolderWithLineage = findItem(documentTree, navFolderToUse.uuid);
        docRefs = navFolderWithLineage.node.children;
        thisFolder = navFolderWithLineage.node;

        if (navFolderWithLineage.lineage && navFolderWithLineage.lineage.length > 0) {
          parentFolder = navFolderWithLineage.lineage[navFolderWithLineage.lineage.length - 1]
        }
      }

      return {
        searchMode,
        searchTerm,
        docRefs,
        thisFolder,
        parentFolder
      };
    },
    {
      searchApp,
      searchTermUpdated,
      navigateToFolder
    },
  ),
  withSelectableItemListing(({ pickerId, docRefs, chooseDocRef, navigateToFolder }) => ({
    listingId: pickerId,
    items: docRefs,
    openItem: chooseDocRef,
    enterItem: d => navigateToFolder(pickerId, d),
    goBack: console.log('Go back to parent folder')
  })),
  withProps(({searchMode, parentFolder, navigateToFolder, pickerId, thisFolder}) => {
    let headerTitle;
    let headerIcon;
    let headerAction = () => {};

    switch (searchMode) {
      case (SEARCH_MODE.GLOBAL_SEARCH): {
        headerIcon = 'search';
        headerTitle = 'Search';
        break;
      }
      case (SEARCH_MODE.NAVIGATION): {
        headerTitle = thisFolder.name;
        if (parentFolder) {
          headerIcon = 'arrow left';
          headerAction = () => navigateToFolder(pickerId, parentFolder)
        } else {
          headerIcon = 'dont';
        }
        break;
      }
      default: 
      break;
    }

    return {
      headerTitle,
      headerIcon,
      headerAction
    }
  })
);

const AppSearchBar = ({
  pickerId,
  docRefs,
  searchMode,
  searchApp,
  chooseDocRef,
  navigateToFolder,
  searchValue,
  searchTermUpdated,
  onKeyDownWithShortcuts,
  isDropDownOpen,
  setDropdownOpen,
  headerTitle,
  headerIcon,
  headerAction
}) => (
  <div
    className="dropdown"
    tabIndex={0}
    onFocus={() => setDropdownOpen(true)}
    onBlur={() => setDropdownOpen(false)}
    onKeyDown={onKeyDownWithShortcuts}
  >
    <Input
      onFocus={() => setDropdownOpen(true)}
      onBlur={() => setDropdownOpen(false)}
      fluid
      className="border flat"
      icon="search"
      placeholder="Search..."
      value={searchValue}
      onChange={({ target: { value } }) => {
        searchTermUpdated(pickerId, value);
        searchApp(pickerId, { term: value });
      }}
    />
    <div className={`dropdown__content ${isDropDownOpen ? 'open' : ''}`}>
    <div className="app-search-header">
        <Icon
          name={headerIcon}
          size='large'
          onClick={headerAction}
        />
        {headerTitle}
      </div>
      <div className="app-search-listing">
      {docRefs.map((searchResult, index) => (
        <DocRefListingEntry
          key={searchResult.uuid}
          index={index}
          listingId={pickerId}
          docRef={searchResult}
          openDocRef={chooseDocRef}
          enterFolder={d => navigateToFolder(pickerId, d)}
        >
          <DocRefBreadcrumb docRefUuid={searchResult.uuid} openDocRef={chooseDocRef} />
        </DocRefListingEntry>
      ))}
      </div>
      <div className="app-search-footer">
        <Button primary>Choose</Button>
      </div>
    </div>
  </div>
);

const EnhancedAppSearchBar = enhance(AppSearchBar);

EnhancedAppSearchBar.propTypes = {
  pickerId: PropTypes.string.isRequired,
  chooseDocRef: PropTypes.func.isRequired,
};

EnhancedAppSearchBar.defaultProps = {
  pickerId: 'global',
};

export default EnhancedAppSearchBar;
