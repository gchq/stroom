import React from 'react';

import { compose, withState } from 'recompose';
import { connect } from 'react-redux';
import { Input } from 'semantic-ui-react';
import { withRouter } from 'react-router-dom';

import { actionCreators as appSearchBarActionCreators } from './redux';
import { searchApp } from 'components/FolderExplorer/explorerClient';
import openDocRef from 'sections/RecentItems/openDocRef';
import { DocRefBreadcrumb } from 'components/DocRefBreadcrumb';
import DocRefListingEntry from 'components/DocRefListingEntry';
import { withDocRefTypes } from 'components/DocRefTypes';
import withSelectableItemListing from 'lib/withSelectableItemListing';

const { searchTermUpdated, searchDocRefTypeChosen } = appSearchBarActionCreators;

const LISTING_ID = 'app-search-bar';

const withDropdownOpen = withState('isDropDownOpen', 'setDropdownOpen', false);

const enhance = compose(
  withRouter,
  withDocRefTypes,
  withDropdownOpen,
  connect(
    ({ appSearch: { searchTerm, searchDocRefType, searchResults } }, props) => ({
      searchValue:
        searchTerm.length > 0 ? searchTerm : searchDocRefType ? `type:${searchDocRefType}` : '',
      searchResults,
    }),
    {
      searchApp,
      searchTermUpdated,
      searchDocRefTypeChosen,
      openDocRef,
    },
  ),
  withSelectableItemListing(({ searchResults, openDocRef, history }) => ({
    listingId: LISTING_ID,
    items: searchResults,
    openItem: d => openDocRef(history, d),
  })),
);

const AppSearchBar = ({
  searchResults,
  searchApp,
  openDocRef,
  searchValue,
  searchTermUpdated,
  searchDocRefTypeChosen,
  history,
  docRefTypes,
  onKeyDownWithShortcuts,
  isDropDownOpen,
  setDropdownOpen,
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
        searchTermUpdated(value);
        searchApp({ term: value });
      }}
    />
    <div className={`dropdown__content ${isDropDownOpen ? 'open' : ''}`}>
      {searchResults.map((searchResult, index) => (
        <DocRefListingEntry
          key={searchResult.uuid}
          index={index}
          listingId={LISTING_ID}
          docRef={searchResult}
          openDocRef={d => openDocRef(history, d)}
        >
          <DocRefBreadcrumb docRefUuid={searchResult.uuid} openDocRef={openDocRef} />
        </DocRefListingEntry>
      ))}
    </div>
  </div>
);

const EnhancedAppSearchBar = enhance(AppSearchBar);

EnhancedAppSearchBar.propTypes = {};

export default EnhancedAppSearchBar;
