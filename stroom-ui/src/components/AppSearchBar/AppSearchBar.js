import React from 'react';

import { compose } from 'recompose';
import { connect } from 'react-redux';
import { Input, Dropdown } from 'semantic-ui-react';

import { actionCreators as appSearchBarActionCreators } from './redux';
import { searchApp } from 'components/FolderExplorer/explorerClient';
import withOpenDocRef from 'sections/RecentItems/withOpenDocRef';
import { DocRefListingEntry } from 'components/DocRefListingEntry';
import { withDocRefTypes } from 'components/DocRefTypes';
import withSelectableItemListing from 'lib/withSelectableItemListing';

const {
  searchTermUpdated,
  searchDocRefTypeChosen
} = appSearchBarActionCreators;

const LISTING_ID = 'app-search-bar';

const enhance = compose(
  withOpenDocRef,
  withDocRefTypes,
  connect(
    ({
      appSearch: {
        searchTerm, searchDocRefType, searchResults, selectedIndex,
      },
    }, props) => ({
      searchValue:
        searchTerm.length > 0 ? searchTerm : searchDocRefType ? `type:${searchDocRefType}` : '',
      selectedIndex,
      searchResults,
    }),
    {
      searchApp,
      searchTermUpdated,
      searchDocRefTypeChosen
    },
  ),
  withSelectableItemListing(({searchResults, openDocRef}) => ({
    listingId: LISTING_ID,
    items: searchResults,
    openItem: openDocRef
  }))
);

const AppSearchBar = ({
  searchResults,
  selectedIndex,
  searchApp,
  openDocRef,
  searchValue,
  searchTermUpdated,
  searchDocRefTypeChosen,
  history,
  docRefTypes,
  enableShortcuts,
  disableShortcuts,
  onKeyDownWithShortcuts                                                                             
}) => (
  <Dropdown
    fluid
    icon={null}
    trigger={
      <Input
        fluid
        className="border flat"
        icon="search"
        placeholder="Search..."
        value={searchValue}
        onKeyDown={onKeyDownWithShortcuts}
        onFocus={enableShortcuts}
        onBlur={disableShortcuts}
        onChange={({ target: { value } }) => {
          searchTermUpdated(value);
          searchApp({ term: value });
        }}
      />
    }
  >
    <Dropdown.Menu className="border flat">
      {searchResults.length === 0 &&
        docRefTypes.map(docRefType => (
          <Dropdown.Item
            className="flat"
            key={docRefType}
            onClick={() => {
              searchApp({ docRefType });
              searchDocRefTypeChosen(docRefType);
            }}
          >
            <img
              className="stroom-icon--small"
              alt="X"
              src={require(`../../images/docRefTypes/${docRefType}.svg`)}
            />
            {docRefType}
          </Dropdown.Item>
        ))}
      {searchResults.length > 0 &&
        searchResults.map((searchResult, index) => (
          <DocRefListingEntry
            key={searchResult.uuid}
            index={index}
            listingId={LISTING_ID}
            docRef={searchResult}
            openDocRef={openDocRef}
            includeBreadcrumb
          />
        ))}
    </Dropdown.Menu>
  </Dropdown>
);

const EnhancedAppSearchBar = enhance(AppSearchBar);

EnhancedAppSearchBar.propTypes = {};

export default EnhancedAppSearchBar;
