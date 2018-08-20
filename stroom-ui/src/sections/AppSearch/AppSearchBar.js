import React from 'react';

import { compose } from 'recompose';
import { connect } from 'react-redux';
import { Input, Dropdown } from 'semantic-ui-react';

import { actionCreators } from './redux';
import { withExplorerTree } from 'components/FolderExplorer';
import withOpenDocRef from 'sections/RecentItems/withOpenDocRef';
import { RawDocRefListingEntry } from 'components/DocRefListing';

const { appSearchTermUpdated } = actionCreators;

const enhance = compose(
  withExplorerTree,
  withOpenDocRef,
  connect(
    ({ appSearch: { searchTerm, searchResults } }, props) => ({ searchTerm, searchResults }),
    {
      appSearchTermUpdated,
    },
  ),
);

const AppSearchBar = ({
  searchTerm, searchResults, appSearchTermUpdated, openDocRef,
}) => (
  <Dropdown
    icon={null}
    trigger={
      <Input
        id="AppSearch__search-input"
        icon="search"
        placeholder="Search..."
        value={searchTerm}
        onChange={e => appSearchTermUpdated(e.target.value)}
        autoFocus
      />
    }
  >
    <Dropdown.Menu>
      {searchResults.map(({ node, lineage }) => (
        <RawDocRefListingEntry
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
