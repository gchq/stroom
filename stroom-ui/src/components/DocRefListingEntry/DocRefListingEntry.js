import React from 'react';
import PropTypes from 'prop-types';

import { connect } from 'react-redux';
import { compose, withHandlers } from 'recompose';

import DocRefPropType from 'lib/DocRefPropType';
import { DocRefBreadcrumb } from 'components/DocRefBreadcrumb';
import { actionCreators as selectableItemActionCreators } from 'lib/withSelectableItemListing';

const { selectionToggled } = selectableItemActionCreators;

const enhance = compose(
  connect(
    ({ keyIsDown, selectableItemListings }, { listingId, docRef }) => {
      const selectableItemListing = selectableItemListings[listingId];
      let isSelected = false;
      if (selectableItemListing) {
        isSelected = selectableItemListing.selectedItems.map(d => d.uuid).includes(docRef.uuid);
      }
      return {
        isSelected,
        keyIsDown,
      };
    },
    { selectionToggled },
  ),
  withHandlers({
    onRowClick: ({
      listingId, index, keyIsDown, selectionToggled,
    }) => (e) => {
      selectionToggled(listingId, index, keyIsDown);
      e.preventDefault();
    },
    onNameClick: ({ openDocRef, docRef }) => (e) => {
      openDocRef(docRef);
      e.stopPropagation();
      e.preventDefault();
    },
  }),
);

const RawDocRefListingEntry = ({
  className, docRef, isSelected, onRowClick, onNameClick,
}) => (
  <div
    className={`hoverable ${className || ''} ${isSelected ? 'selected' : ''}`}
    onClick={onRowClick}
  >
    <img
      className="stroom-icon--large"
      alt="X"
      src={require(`../../images/docRefTypes/${docRef.type}.svg`)}
    />
    <span className="doc-ref-listing__name" onClick={onNameClick}>
      {docRef.name}
    </span>
  </div>
);

const RawDocRefListingEntryWithBreadcrumb = ({
  className,
  docRef,
  openDocRef,
  isSelected,
  onRowClick,
  onNameClick,
}) => (
  <div
    className={`hoverable ${className || ''} ${isSelected ? 'selected' : ''}`}
    onClick={onRowClick}
  >
    <div>
      <img
        className="stroom-icon--large"
        alt="X"
        src={require(`../../images/docRefTypes/${docRef.type}.svg`)}
      />
      <span className="doc-ref-listing__name" onClick={onNameClick}>
        {docRef.name}
      </span>
    </div>

    <DocRefBreadcrumb docRefUuid={docRef.uuid} openDocRef={openDocRef} />
  </div>
);

const DocRefListingEntry = enhance(RawDocRefListingEntry);
const DocRefListingEntryWithBreadcrumb = enhance(RawDocRefListingEntryWithBreadcrumb);

[DocRefListingEntry, DocRefListingEntryWithBreadcrumb].forEach(d =>
  (d.propTypes = {
    listingId: PropTypes.string.isRequired,
    docRef: DocRefPropType,
    index: PropTypes.number.isRequired,
    className: PropTypes.string,
    isSelected: PropTypes.bool,
    openDocRef: PropTypes.func.isRequired,
  }));

export default DocRefListingEntry;

export { DocRefListingEntry, DocRefListingEntryWithBreadcrumb };
