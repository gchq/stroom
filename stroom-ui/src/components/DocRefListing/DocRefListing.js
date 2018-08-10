import React from 'react';
import PropTypes from 'prop-types';
import { compose, lifecycle, branch, renderComponent, withProps } from 'recompose';
import { connect } from 'react-redux';
import { withRouter } from 'react-router-dom';
import { Header, Icon, Grid, Input, Popup, Button, Loader } from 'semantic-ui-react';
import Mousetrap from 'mousetrap';

import { DocTypeFilters } from 'components/DocRefTypes';
import ClickCounter from 'lib/ClickCounter';
import DocRefPropType from 'lib/DocRefPropType';
import DocRefListingEntry from './DocRefListingEntry';
import openDocRef from 'prototypes/RecentItems/openDocRef';
import DocRefBreadcrumb from 'components/DocRefBreadcrumb';
import { actionCreators } from './redux';

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
    ({ docRefListing }, { listingId }) => ({
      docRefListing: docRefListing[listingId],
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
    docRefSelectionUp,
    docRefSelectionDown,
    docRefListingUnmounted,
  }) => {
    const {
      selectedDocRef, 
      filteredDocRefs
    } = docRefListing || {};
    let onOpenKey = () => {
      if (selectedDocRef !== undefined) {
        openDocRef(history, selectedDocRef);
      } else if (filteredDocRefs.length > 0) {
        openDocRef(history, filteredDocRefs[0]);
      }
    }
    let onUpKey = () => {
      docRefSelectionUp(listingId);
    };
    let onDownKey = () => {
      docRefSelectionDown(listingId);
    };
    let onSearchInputKeyDown = (e) => {
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
    return {
      onOpenKey,
      onUpKey,
      onDownKey,
      onSearchInputKeyDown
    }
  }),
  lifecycle({
    componentDidUpdate(prevProps, prevState, snapshot) {
      const {
        parentFolder, listingId, docRefs, docRefListingMounted,
      } = this.props;

      if (parentFolder && parentFolder.uuid !== prevProps.parentFolder.uuid) {
        docRefListingMounted(listingId, docRefs);
      }
    },
    componentDidMount() {
      const {
        docRefListingMounted,
        docRefListingUnmounted,
        onUpKey,
        onDownKey,
        listingId,
        docRefs,
        openDocRef,
        onOpenKey,
        history,
        selectedDocRef,
        alwaysFilter,
      } = this.props;
      docRefListingMounted(listingId, docRefs, alwaysFilter);

      Mousetrap.bind(upKeys, onUpKey);
      Mousetrap.bind(downKeys, onDownKey);
      Mousetrap.bind(openKeys, onOpenKey);
    },
    componentWillUnmount() {
      Mousetrap.unbind(upKeys);
      Mousetrap.unbind(downKeys);
      Mousetrap.unbind(openKeys);
      docRefListingUnmounted(this.props.listingId);
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
  docRefListing: { selectedDocRef, filterTerm, filteredDocRefs, docRefTypeFilters },
  filterTermUpdated,
  docRefTypeFilterUpdated,
  parentFolder,
  onSearchInputKeyDown,
}) => {
  // const clickCounter = new ClickCounter()
  //   .withOnSingleClick(({ index }) => onRowSelected(folderUuid, index))
  //   .withOnDoubleClick(d => openDocRef(d));

  return (
    <React.Fragment>
      <Grid className="content-tabs__grid">
        <Grid.Column width={10}>
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
          <Input
            id="AppSearch__search-input"
            icon="search"
            placeholder="Search..."
            value={filterTerm}
            onChange={e => filterTermUpdated(listingId, e.target.value)}
            autoFocus
            onKeyDown={onSearchInputKeyDown}
          />
          <Popup trigger={<Button icon="filter" />} flowing hoverable>
            <DocTypeFilters value={docRefTypeFilters} onChange={v => docRefTypeFilterUpdated(listingId, v)} />
          </Popup>
        </Grid.Column>
      </Grid>
      <div className="doc-ref-listing">
        {filteredDocRefs.map(docRef => (
          <DocRefListingEntry key={docRef.uuid} docRef={docRef} selectedDocRef={selectedDocRef} />
        ))}
      </div>
    </React.Fragment>
  );
};

const EnhancedDocRefListing = enhance(DocRefListing);

EnhancedDocRefListing.propTypes = {
  icon: PropTypes.string.isRequired,
  title: PropTypes.string.isRequired,
  listingId: PropTypes.string.isRequired,
  parentFolder: DocRefPropType,
  docRefs: PropTypes.arrayOf(DocRefPropType).isRequired,
  alwaysFilter: PropTypes.bool.isRequired,
};

EnhancedDocRefListing.defaultProps = {
  alwaysFilter: false,
};

export default EnhancedDocRefListing;
