import React from 'react';
import PropTypes from 'prop-types';

import { compose, withHandlers, withProps, withState } from 'recompose';
import { connect } from 'react-redux';
import { Input, Icon } from 'semantic-ui-react';

import DocRefPropType from 'lib/DocRefPropType';
import { findItem, filterTree } from 'lib/treeUtils';
import {
  actionCreators as appSearchBarActionCreators,
  defaultPickerState,
  SEARCH_MODE,
} from './redux';
import { searchApp } from 'components/FolderExplorer/explorerClient';
import { DocRefBreadcrumb } from 'components/DocRefBreadcrumb';
import DocRefListingEntry from 'components/DocRefListingEntry';
import withSelectableItemListing, {
  defaultSelectableItemListingState,
} from 'lib/withSelectableItemListing';
import withDocumentTree from 'components/FolderExplorer/withDocumentTree';

import ModeOptionButtons from './ModeOptionButtons';

const { searchTermUpdated, navigateToFolder } = appSearchBarActionCreators;

const withTextFocus = withState('textFocus', 'setTextFocus', false);

const enhance = compose(
  withDocumentTree,
  withTextFocus,
  connect(
    (
      {
        appSearch, recentItems, selectableItemListings, folderExplorer: { documentTree },
      },
      {
        pickerId, typeFilters, value, textFocus,
      },
    ) => {
      const appSearchForPicker = appSearch[pickerId] || defaultPickerState;
      const selectableItemListing =
        selectableItemListings[pickerId] || defaultSelectableItemListingState;
      const {
        searchTerm, navFolder, searchResults, searchMode,
      } = appSearchForPicker;
      const documentTreeToUse =
        typeFilters.length > 0
          ? filterTree(documentTree, d => typeFilters.includes(d.type))
          : documentTree;

      let docRefs;
      let thisFolder;
      let parentFolder;
      let valueToShow;

      if (textFocus) {
        valueToShow = searchTerm;
      } else if (value) {
        valueToShow = value.name;
      } else {
        valueToShow = '';
      }

      switch (searchMode) {
        case SEARCH_MODE.NAVIGATION: {
          const navFolderToUse = navFolder || documentTreeToUse;
          const navFolderWithLineage = findItem(documentTreeToUse, navFolderToUse.uuid);
          docRefs = navFolderWithLineage.node.children;
          thisFolder = navFolderWithLineage.node;

          if (navFolderWithLineage.lineage && navFolderWithLineage.lineage.length > 0) {
            parentFolder = navFolderWithLineage.lineage[navFolderWithLineage.lineage.length - 1];
          }
          break;
        }
        case SEARCH_MODE.GLOBAL_SEARCH: {
          docRefs = searchResults;
          break;
        }
        case SEARCH_MODE.RECENT_ITEMS: {
          docRefs = recentItems;
          break;
        }
        default:
          docRefs = [];
          break;
      }

      return {
        searchTerm,
        searchMode,
        valueToShow,
        selectableItemListing,
        docRefs,
        hasNoResults: docRefs.length === 0,
        noResultsText: searchMode === SEARCH_MODE.NAVIGATION ? 'empty' : 'no results',
        provideBreadcrumbs: searchMode !== SEARCH_MODE.NAVIGATION,
        thisFolder,
        parentFolder,
      };
    },
    {
      searchApp,
      searchTermUpdated,
      navigateToFolder,
    },
  ),
  withSelectableItemListing(({
    pickerId, docRefs, navigateToFolder, parentFolder, onChange,
  }) => ({
    listingId: pickerId,
    items: docRefs,
    openItem: onChange,
    enterItem: d => navigateToFolder(pickerId, d),
    goBack: () => navigateToFolder(pickerId, parentFolder),
  })),
  withHandlers({
    onTextFocus: ({ setTextFocus }) => e => setTextFocus(true),
    onTextBlur: ({ setTextFocus }) => e => setTextFocus(false),
    onSearchTermChange: ({ pickerId, searchTermUpdated, searchApp }) => ({ target: { value } }) => {
      searchTermUpdated(pickerId, value);
      searchApp(pickerId, { term: value });
    },
    thisNavigateToFolder: ({navigateToFolder, pickerId}) => d => navigateToFolder(pickerId, d)
  }),
  withProps(({
    pickerId, searchMode, thisFolder, parentFolder, thisNavigateToFolder, searchTerm,
  }) => {
    let headerTitle;
    let headerIcon;
    let headerAction = () => {};

    switch (searchMode) {
      case SEARCH_MODE.NAVIGATION: {
        headerTitle = thisFolder.name;
        if (parentFolder) {
          headerIcon = 'arrow left';
          headerAction = () => thisNavigateToFolder(parentFolder);
        } else {
          headerIcon = 'folder';
        }
        break;
      }
      case SEARCH_MODE.GLOBAL_SEARCH: {
        headerIcon = 'search';
        headerTitle = `Search for '${searchTerm}'`;
        break;
      }
      case SEARCH_MODE.RECENT_ITEMS: {
        headerTitle = 'Recent Items';
        headerIcon = 'history';
        break;
      }
      default:
        break;
    }

    return {
      headerTitle,
      headerIcon,
      headerAction,
    };
  }),
);

const AppSearchBar = ({
  pickerId,
  docRefs,
  thisNavigateToFolder,
  onKeyDownWithShortcuts,
  headerTitle,
  headerIcon,
  headerAction,
  hasNoResults,
  noResultsText,
  provideBreadcrumbs,
  onChange,
  valueToShow,
  onTextFocus,
  onTextBlur,
  onSearchTermChange,
}) => (
  <div className="dropdown app-search-bar">
    <Input
      className="border flat app-search-bar__input"
      icon="search"
      placeholder="Search..."
      value={valueToShow}
      onFocus={onTextFocus}
      onBlur={onTextBlur}
      onChange={onSearchTermChange}
    />
    <div tabIndex={0} onKeyDown={onKeyDownWithShortcuts} className="dropdown__content">
      <div className="app-search-header">
        <Icon name={headerIcon} size="large" onClick={headerAction} />
        {headerTitle}
        <ModeOptionButtons pickerId={pickerId} />
      </div>
      <div className="app-search-listing">
        {hasNoResults && <div className="app-search-listing__empty">{noResultsText}</div>}
        {docRefs.map((searchResult, index) => (
          <DocRefListingEntry
            key={searchResult.uuid}
            index={index}
            listingId={pickerId}
            docRef={searchResult}
            openDocRef={onChange}
            enterFolder={thisNavigateToFolder}
          >
            {provideBreadcrumbs && (
              <DocRefBreadcrumb
                docRefUuid={searchResult.uuid}
                openDocRef={thisNavigateToFolder}
              />
            )}
          </DocRefListingEntry>
        ))}
      </div>
    </div>
  </div>
);

const EnhancedAppSearchBar = enhance(AppSearchBar);

EnhancedAppSearchBar.propTypes = {
  pickerId: PropTypes.string.isRequired,
  typeFilters: PropTypes.array.isRequired,
  onChange: PropTypes.func.isRequired,
  value: DocRefPropType,
};

EnhancedAppSearchBar.defaultProps = {
  pickerId: 'global',
  typeFilters: [],
  onChange: () => console.log('onChange not provided for app search bar'),
};

export default EnhancedAppSearchBar;
