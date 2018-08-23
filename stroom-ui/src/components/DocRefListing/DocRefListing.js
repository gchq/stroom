import React from 'react';
import PropTypes from 'prop-types';
import { Header, Icon, Grid, Button, Loader } from 'semantic-ui-react/dist/commonjs';

import DocRefPropType from 'lib/DocRefPropType';
import ThemedPopup from 'components/ThemedPopup'
import DocRefListingEntry from './DocRefListingEntry';
import DndDocRefListingEntry from './DndDocRefListingEntry';
import DocRefBreadcrumb from 'components/DocRefBreadcrumb';
import ActionBarItemsPropType from './ActionBarItemsPropType';
import AppSearchBar from 'components/AppSearchBar';
import withSelectableItemListing from 'lib/withSelectableItemListing';

const enhance = withSelectableItemListing(({ listingId, openDocRef, docRefs, allowMultiSelect }) => ({
  listingId,
  openItem: openDocRef,
  items: docRefs,
  allowMultiSelect
}));

const DocRefListing = ({
  listingId,
  icon,
  title,
  openDocRef,
  includeBreadcrumbOnEntries,
  actionBarItems,
  parentFolder,
  hasTypesFilteredOut,
  docRefs,
}) => (
  <React.Fragment>
    <Grid className="content-tabs__grid">
      <Grid.Column width={16}>
        <AppSearchBar />
      </Grid.Column>
      <Grid.Column width={actionBarItems.length > 0 ? 11 : 16}>
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
      {actionBarItems.length > 0 && (
        <Grid.Column width={5}>
          <span className="doc-ref-listing-entry__action-bar">
            {actionBarItems.map(({
 onClick, icon, tooltip, disabled,
}, i) => (
  <ThemedPopup
    key={i}
    trigger={
      <Button
        className="action-bar__button"
        circular
        onClick={onClick}
        icon={icon}
        disabled={disabled}
      />
                }
    content={tooltip}
  />
            ))}
          </span>
        </Grid.Column>
      )}
    </Grid>
    <div className="doc-ref-listing">
      {docRefs.map((docRef, index) => (
        <DndDocRefListingEntry
          key={docRef.uuid}
          index={index}
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
  docRefs: PropTypes.arrayOf(DocRefPropType).isRequired,
  actionBarItems: ActionBarItemsPropType.isRequired,
  openDocRef: PropTypes.func.isRequired,
};

EnhancedDocRefListing.defaultProps = {
  actionBarItems: [],
  docRefs: [],
  includeBreadcrumbOnEntries: true,
  allowMultiSelect: false,
};

export default EnhancedDocRefListing;
