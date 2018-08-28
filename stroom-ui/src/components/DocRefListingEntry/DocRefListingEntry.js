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

const DocRefListingEntry = ({
  className,
  docRef,
  openDocRef,
  includeBreadcrumb,
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
        {docRef.name} {isSelected ? 'selected' : ''}
      </span>
    </div>

    {includeBreadcrumb && <DocRefBreadcrumb docRefUuid={docRef.uuid} openDocRef={openDocRef} />}
  </div>
);

DocRefListingEntry.propTypes = {
  index: PropTypes.number.isRequired,
  className: PropTypes.string,
  docRef: DocRefPropType,
  isSelected: PropTypes.bool.isRequired,
  openDocRef: PropTypes.func.isRequired,
};

DocRefListingEntry.defaultProps = {
  includeBreadcrumb: true,
  isSelected: false,
};

export default enhance(DocRefListingEntry);
