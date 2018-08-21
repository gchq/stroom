import React from 'react';
import PropTypes from 'prop-types';
import { compose, lifecycle, branch, renderComponent, withProps } from 'recompose';
import { connect } from 'react-redux';
import { Header, Icon, Grid, Popup, Button, Loader } from 'semantic-ui-react/dist/commonjs';
import Mousetrap from 'mousetrap';

import DocRefPropType from 'lib/DocRefPropType';
import DocRefListingEntry from './DocRefListingEntry';
import DocRefBreadcrumb from 'components/DocRefBreadcrumb';
import { actionCreators } from './redux';
import ActionBarItemsPropType from './ActionBarItemsPropType';

const upKeys = ['k', 'ctrl+k', 'up'];
const downKeys = ['j', 'ctrl+j', 'down'];
const openKeys = ['enter'];

const {
  docRefListingMounted,
  docRefListingUnmounted,
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

// // Up
// const upKeycode = 38;
// const kKeycode = 75;

// // Down
// const downKeycode = 40;
// const jKeycode = 74;

// const enterKeycode = 13;

const enhance = compose(
  connect(
    ({ docRefListing, docRefTypes }, { listingId }) => ({
      docRefListing: docRefListing[listingId],
      docRefTypes,
    }),
    {
      docRefListingMounted,
      docRefListingUnmounted,
      docRefSelectionUp,
      docRefSelectionDown,
    },
  ),
  withProps(({
    listingId,
    docRefListing,
    openDocRef,
    docRefSelectionUp,
    docRefSelectionDown,
    docRefListingUnmounted,
  }) => {
    const { selectedDocRef, allDocRefs } = docRefListing || {};
    const onOpenKey = () => {
      if (selectedDocRef !== undefined) {
        openDocRef(selectedDocRef);
      } else if (allDocRefs.length > 0) {
        openDocRef(allDocRefs[0]);
      }
    };
    const onUpKey = () => {
      docRefSelectionUp(listingId);
    };
    const onDownKey = () => {
      docRefSelectionDown(listingId);
    };

    return {
      onOpenKey,
      onUpKey,
      onDownKey,
    };
  }),
  lifecycle({
    componentDidUpdate(prevProps, prevState, snapshot) {
      const {
        listingId, allDocRefs, docRefListingMounted, allowMultiSelect
      } = this.props;

      const docRefsChanged = JSON.stringify(allDocRefs) !== JSON.stringify(prevProps.allDocRefs);

      if (docRefsChanged) {
        docRefListingMounted(listingId, allDocRefs, allowMultiSelect);
      }
    },
    componentDidMount() {
      const {
        docRefListingMounted,
        onUpKey,
        onDownKey,
        listingId,
        allDocRefs,
        onOpenKey,
        allowMultiSelect,
      } = this.props;
      
      docRefListingMounted(listingId, allDocRefs, allowMultiSelect);

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
    filterTerm, allDocRefs, docRefTypeFilters
  },
  openDocRef,
  includeBreadcrumbOnEntries,
  actionBarItems,
  parentFolder,
  hasTypesFilteredOut,
}) => (
  <React.Fragment>
    <Grid className="content-tabs__grid">
      <Grid.Column width={(actionBarItems.length > 0) ? 11 : 16}>
        <Header as="h3">
          <Icon name={icon} />
          <Header.Content>{title}</Header.Content>
          {parentFolder && (
            <Header.Subheader>
              <DocRefBreadcrumb docRefUuid={parentFolder.uuid} openDocRef={openDocRef} />
            </Header.Subheader>
          )}
        </Header>
      </Grid.Column>
      {(actionBarItems.length > 0) && <Grid.Column width={5}>
        <span className="doc-ref-listing-entry__action-bar">
          {actionBarItems.map(({ onClick, icon, tooltip, disabled }, i) => (
            <Popup
              key={i}
              trigger={<Button className='action-bar__button' circular onClick={onClick} icon={icon} disabled={disabled} />}
              content={tooltip}
            />
          ))}
        </span>
      </Grid.Column>
      }
    </Grid>
    <div className="doc-ref-listing">
      {allDocRefs.map(docRef => (
        <DocRefListingEntry
          key={docRef.uuid}
          listingId={listingId}
          docRefUuid={docRef.uuid}
          includeBreadcrumb={includeBreadcrumbOnEntries}
          onNameClick={node => openDocRef(node)}
          openDocRef={openDocRef}
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
  allDocRefs: PropTypes.arrayOf(DocRefPropType).isRequired,
  allowMultiSelect: PropTypes.bool.isRequired,
  actionBarItems: ActionBarItemsPropType.isRequired,
  openDocRef: PropTypes.func.isRequired,
};

EnhancedDocRefListing.defaultProps = {
  actionBarItems: [],
  allDocRefs: [],
  includeBreadcrumbOnEntries: true,
  allowMultiSelect: false,
};

export default EnhancedDocRefListing;
