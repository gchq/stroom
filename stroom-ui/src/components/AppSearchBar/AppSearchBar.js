import React from 'react';
import PropTypes from 'prop-types';

import { compose, withState } from 'recompose';
import { connect } from 'react-redux';
import { Input } from 'semantic-ui-react';

import { actionCreators as appSearchBarActionCreators, defaultPickerState } from './redux';
import { searchApp } from 'components/FolderExplorer/explorerClient';
import { DocRefBreadcrumb } from 'components/DocRefBreadcrumb';
import DocRefListingEntry from 'components/DocRefListingEntry';
import { withDocRefTypes } from 'components/DocRefTypes';
import withSelectableItemListing from 'lib/withSelectableItemListing';

const { searchTermUpdated } = appSearchBarActionCreators;

const withDropdownOpen = withState('isDropDownOpen', 'setDropdownOpen', false);

const enhance = compose(
  withDocRefTypes,
  withDropdownOpen,
  connect(
    ({ appSearch }, { pickerId }) => {
      const appSearchForPicker = appSearch[pickerId] || defaultPickerState;
      const { searchTerm, searchResults } = appSearchForPicker;
      return {
        searchTerm,
        searchResults,
      };
    },
    {
      searchApp,
      searchTermUpdated,
    },
  ),
  withSelectableItemListing(({ pickerId, searchResults, chooseDocRef }) => ({
    listingId: pickerId,
    items: searchResults,
    openItem: d => chooseDocRef(d),
  })),
);

const AppSearchBar = ({
  pickerId,
  searchResults,
  searchApp,
  chooseDocRef,
  searchValue,
  searchTermUpdated,
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
        searchTermUpdated(pickerId, value);
        searchApp(pickerId, { term: value });
      }}
    />
    <div className={`dropdown__content ${isDropDownOpen ? 'open' : ''}`}>
      {searchResults.map((searchResult, index) => (
        <DocRefListingEntry
          key={searchResult.uuid}
          index={index}
          listingId={pickerId}
          docRef={searchResult}
          openDocRef={chooseDocRef}
        >
          <DocRefBreadcrumb docRefUuid={searchResult.uuid} openDocRef={chooseDocRef} />
        </DocRefListingEntry>
      ))}
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
