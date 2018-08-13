import React from 'react';
import PropTypes from 'prop-types';
import { compose, lifecycle, branch, renderComponent, withProps } from 'recompose';
import { connect } from 'react-redux';
import { withRouter } from 'react-router-dom';
import { Header, Icon, Grid, Input, Popup, Button, Loader } from 'semantic-ui-react';
import Mousetrap from 'mousetrap';

import { DocTypeFilters } from 'components/DocRefTypes';
import DocRefPropType from 'lib/DocRefPropType';
import DocRefListingEntry from './DocRefListingEntry';
import openDocRef from 'prototypes/RecentItems/openDocRef';
import DocRefBreadcrumb from 'components/DocRefBreadcrumb';
import { actionCreators } from './redux';
import ActionBarItemsPropType from './ActionBarItemsPropType';

const upKeys = ['k', 'ctrl+k', 'up'];
const downKeys = ['j', 'ctrl+j', 'down'];
const openKeys = ['enter'];

const {
  docRefListingMounted,
  docRefListingUnmounted,
  filterTermUpdated,
  docRefTypeFilterUpdated,
  docRefSelectionUp,
  docRefSelectionDown,
} = actionCreators;

// We need to prevent up and down keys from moving the cursor around in the input

// I'd rather use Mousetrap for these shortcut keys. Historically Mousetrap
// hasn't handled keypresses that occured inside inputs or textareas.
// There were some changes to fix this, like binding specifically
// to a field. But that requires getting the element from the DOM and
// we'd rather not break outside React to do this. The other alternative
// is adding 'mousetrap' as a class to the input, but that doesn't seem to work.

// Up
const upKeycode = 38;
const kKeycode = 75;

// Down
const downKeycode = 40;
const jKeycode = 74;

const enterKeycode = 13;

const enhance = compose(
  withRouter,
  connect(
    ({ docRefListing, docRefTypes }, { listingId }) => ({
      docRefListing: docRefListing[listingId],
      docRefTypes,
    }),
    {
      docRefListingMounted,
      docRefListingUnmounted,
      filterTermUpdated,
      docRefTypeFilterUpdated,
      docRefSelectionUp,
      docRefSelectionDown,
      openDocRef,
    },
  ),
  withProps(({
    listingId,
    docRefListing,
    openDocRef,
    history,
    docRefTypes,
    docRefSelectionUp,
    docRefSelectionDown,
    docRefListingUnmounted,
  }) => {
    const { selectedDocRef, checkedDocRefs, filteredDocRefs, docRefTypeFilters = [] } = docRefListing || {};
    const onOpenKey = () => {
      if (selectedDocRef !== undefined) {
        openDocRef(history, selectedDocRef);
      } else if (filteredDocRefs.length > 0) {
        openDocRef(history, filteredDocRefs[0]);
      }
    };
    const onUpKey = () => {
      docRefSelectionUp(listingId);
    };
    const onDownKey = () => {
      docRefSelectionDown(listingId);
    };
    const onSearchInputKeyDown = (e) => {
      if (e.keyCode === upKeycode || (e.ctrlKey && e.keyCode === kKeycode)) {
        onUpKey();
        e.preventDefault();
      } else if (e.keyCode === downKeycode || (e.ctrlKey && e.keyCode === jKeycode)) {
        onDownKey();
        e.preventDefault();
      } else if (e.keyCode === enterKeycode) {
        onOpenKey();
        e.preventDefault();
      }
    };

    const hasTypesFilteredOut = docRefTypes.length !== docRefTypeFilters.length;
    return {
      onOpenKey,
      onUpKey,
      onDownKey,
      onSearchInputKeyDown,
      hasTypesFilteredOut,
    };
  }),
  lifecycle({
    componentDidUpdate(prevProps, prevState, snapshot) {
      const {
        parentFolder, listingId, docRefs, docRefListingMounted,
      } = this.props;

      const parentFolderChanged = parentFolder && parentFolder.uuid !== prevProps.parentFolder.uuid;
      const docRefsChanged = JSON.stringify(docRefs) !== JSON.stringify(prevProps.docRefs);

      if (parentFolderChanged || docRefsChanged) {
        docRefListingMounted(listingId, docRefs);
      }
    },
    componentDidMount() {
      const {
        docRefListingMounted,
        onUpKey,
        onDownKey,
        listingId,
        docRefs,
        onOpenKey,
        alwaysFilter,
      } = this.props;
      docRefListingMounted(listingId, docRefs, alwaysFilter);

      Mousetrap.bind(upKeys, onUpKey);
      Mousetrap.bind(downKeys, onDownKey);
      Mousetrap.bind(openKeys, onOpenKey);
    },
    componentWillUnmount() {
      const { listingId, docRefListingUnmounted } = this.props;
      Mousetrap.unbind(upKeys);
      Mousetrap.unbind(downKeys);
      Mousetrap.unbind(openKeys);
      docRefListingUnmounted(listingId);
    },
  }),
  branch(
    ({ docRefListing }) => !docRefListing,
    renderComponent(() => <Loader active>Creating Doc Ref Listing</Loader>),
  ),
);

const DocRefListing = ({
  listingId,
  icon,
  title,
  docRefListing: {
    filterTerm, filteredDocRefs, docRefTypeFilters
  },
  includeBreadcrumbOnEntries,
  folderActionBarItems,
  docRefActionBarItems,
  filterTermUpdated,
  docRefTypeFilterUpdated,
  parentFolder,
  onSearchInputKeyDown,
  hasTypesFilteredOut,
}) => (
  <React.Fragment>
    <Grid className="content-tabs__grid">
      <Grid.Column width={6}>
        <Header as="h3">
          <Icon color="grey" name={icon} />
          <Header.Content>{title}</Header.Content>
          {parentFolder && (
            <Header.Subheader>
              <DocRefBreadcrumb docRefUuid={parentFolder.uuid} />
            </Header.Subheader>
          )}
        </Header>
      </Grid.Column>

      <Grid.Column width={6}>
        <div className='doc-ref-listing-entry__search-bar'>
          <Input
            id="AppSearch__search-input"
            icon="search"
            placeholder="Search..."
            value={filterTerm}
            onChange={e => filterTermUpdated(listingId, e.target.value)}
            autoFocus
            onKeyDown={onSearchInputKeyDown}
          />
          <Popup
            trigger={<Button icon="filter" color={hasTypesFilteredOut ? 'blue' : 'grey'} />}
            flowing
            hoverable
          >
            <DocTypeFilters
              value={docRefTypeFilters}
              onChange={v => docRefTypeFilterUpdated(listingId, v)}
            />
          </Popup>
        </div>
      </Grid.Column>
      <Grid.Column width={4}>
        <span className="doc-ref-listing-entry__action-bar">
          {folderActionBarItems.map(({ onClick, icon, tooltip, disabled }, i) => (
            <Popup
              key={i}
              trigger={<Button circular onClick={() => onClick(parentFolder)} icon={icon} disabled={disabled} />}
              content={tooltip}
            />
          ))}
        </span>
      </Grid.Column>
    </Grid>
    <div className="doc-ref-listing">
      {filteredDocRefs.map(docRef => (
        <DocRefListingEntry
          key={docRef.uuid}
          listingId={listingId}
          docRefUuid={docRef.uuid}
          actionBarItems={docRefActionBarItems}
          includeBreadcrumb={includeBreadcrumbOnEntries}
        />
      ))}
    </div>
  </React.Fragment>
);
const EnhancedDocRefListing = enhance(DocRefListing);

EnhancedDocRefListing.propTypes = {
  icon: PropTypes.string.isRequired,
  title: PropTypes.string.isRequired,
  listingId: PropTypes.string.isRequired,
  parentFolder: DocRefPropType,
  includeBreadcrumbOnEntries: PropTypes.bool.isRequired,
  docRefs: PropTypes.arrayOf(DocRefPropType).isRequired,
  alwaysFilter: PropTypes.bool.isRequired,
  inMultiSelectMode: PropTypes.bool.isRequired,
  folderActionBarItems: ActionBarItemsPropType.isRequired,
  docRefActionBarItems: ActionBarItemsPropType.isRequired,
};

EnhancedDocRefListing.defaultProps = {
  alwaysFilter: false,
  folderActionBarItems: [],
  docRefActionBarItems: [],
  includeBreadcrumbOnEntries: true,
  inMultiSelectMode: false,
};

export default EnhancedDocRefListing;
