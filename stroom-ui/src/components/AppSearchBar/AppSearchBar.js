import React from 'react';

import { compose, withState } from 'recompose';
import { connect } from 'react-redux';
import { Input, Dropdown } from 'semantic-ui-react';

import { searchApp } from 'components/FolderExplorer/explorerClient';
import withOpenDocRef from 'sections/RecentItems/withOpenDocRef';
import { RawDocRefListingEntry } from 'components/DocRefListing';

const withSearchTerm = withState('searchTerm', 'setSearchTerm', '');

const enhance = compose(
  withOpenDocRef,
  withSearchTerm,
  connect(({ appSearch: { searchResults } }, props) => ({ searchResults }), {
    searchApp,
  }),
);

const AppSearchBar = ({
  searchResults, searchApp, openDocRef, searchTerm, setSearchTerm,
}) => (
  <Dropdown
    fluid
    icon={null}
    trigger={
      <Input
        id="AppSearch__search-input"
        icon="search"
        placeholder="Search..."
        value={searchTerm}
        onChange={({ target: { value } }) => {
          setSearchTerm(value);
          searchApp(value);
        }}
        autoFocus
      />
    }
  >
    <Dropdown.Menu>
      {searchResults.length === 0 && <Dropdown.Item text="no results" />}
      {searchResults.length > 0 &&
        searchResults.map(({ node, lineage }) => (
          <RawDocRefListingEntry
            key={node.uuid}
            onRowClick={() => console.log('Row Click', node)}
            onNameClick={() => console.log('Name Click', node)}
            node={node}
            openDocRef={openDocRef}
          />
        ))}
    </Dropdown.Menu>
  </Dropdown>
);

const EnhancedAppSearchBar = enhance(AppSearchBar);

EnhancedAppSearchBar.propTypes = {};

export default EnhancedAppSearchBar;
