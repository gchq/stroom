import React from 'react';

import { compose } from 'recompose';
import { connect } from 'react-redux';
import { Input, Dropdown } from 'semantic-ui-react';

import { actionCreators } from './redux';
import { searchApp } from 'components/FolderExplorer/explorerClient';
import withOpenDocRef from 'sections/RecentItems/withOpenDocRef';
import { DocRefListingEntry } from 'components/DocRefListing';
import { onSearchInputKeyDown } from 'lib/KeyCodes';
import { withDocRefTypes } from 'components/DocRefTypes';

const {
  searchTermUpdated,
  searchDocRefTypeChosen,
  searchSelectionUp,
  searchSelectionDown,
  searchSelectionSet,
} = actionCreators;

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
      searchDocRefTypeChosen,
      searchSelectionUp,
      searchSelectionDown,
      searchSelectionSet,
    },
  ),
);

const AppSearchBar = ({
  searchResults,
  selectedIndex,
  searchApp,
  openDocRef,
  searchValue,
  searchTermUpdated,
  searchDocRefTypeChosen,
  searchSelectionUp,
  searchSelectionDown,
  searchSelectionSet,
  history,
  docRefTypes,
}) => (
  <Dropdown
    fluid
    icon={null}
    trigger={
      <Input
        fluid
        className="AppSearch__search-input"
        icon="search"
        placeholder="Search..."
        value={searchValue}
        onKeyDown={onSearchInputKeyDown({
          onUpKey: () => {
            searchSelectionUp();
          },
          onDownKey: () => {
            searchSelectionDown();
          },
          onOpenKey: () => {
            const selectedResult = searchResults[selectedIndex];
            if (selectedResult) {
              openDocRef(selectedResult);
            } else {
              history.push('/s/search');
            }
          },
          onOtherKey: (e) => {
            console.log('Other key', e);
          },
        })}
        onChange={({ target: { value } }) => {
          searchTermUpdated(value);
          searchApp({ term: value });
        }}
      />
    }
  >
    <Dropdown.Menu className="AppSearch__menu">
      {searchResults.length === 0 &&
        docRefTypes.map(docRefType => (
          <Dropdown.Item
            className="AppSearch__dropdown-item"
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
            index={index}
            key={searchResult.uuid}
            onRowClick={() => searchSelectionSet(index)}
            onNameClick={() => openDocRef(searchResult)}
            node={searchResult}
            openDocRef={openDocRef}
            isSelected={index === selectedIndex}
          />
        ))}
    </Dropdown.Menu>
  </Dropdown>
);

const EnhancedAppSearchBar = enhance(AppSearchBar);

EnhancedAppSearchBar.propTypes = {};

export default EnhancedAppSearchBar;
