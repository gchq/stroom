import React from 'react';
import PropTypes from 'prop-types';

import { connect } from 'react-redux';

import DocRefPropType from 'lib/DocRefPropType';
import { DocRefBreadcrumb } from 'components/DocRefBreadcrumb';
import { actionCreators as selectableItemActionCreators } from 'lib/withSelectableItemListing';

const { selectionToggled } = selectableItemActionCreators;

const enhance = connect(
  ({ keyIsDown, selectableItemListings }, { listingId }) => ({
    selectableItemListing: selectableItemListings[listingId],
    keyIsDown,
  }),
  { selectionToggled },
);

const DocRefListingEntry = ({
  className,
  docRef,
  openDocRef,
  includeBreadcrumb,
  selectableItemListing: { selectedItems },
  index,
  keyIsDown,
  listingId,
  selectionToggled,
}) => (
  <div
    className={`hoverable ${className} ${
      selectedItems.map(d => d.uuid).includes(docRef.uuid) ? 'selected' : ''
    }`}
    onClick={(e) => {
      selectionToggled(listingId, index, keyIsDown);
      e.preventDefault();
    }}
  >
    <div>
      <img
        className="stroom-icon--large"
        alt="X"
        src={require(`../../images/docRefTypes/${docRef.type}.svg`)}
      />
      <span
        className="doc-ref-listing__name"
        onClick={(e) => {
          openDocRef(docRef);
          e.stopPropagation();
          e.preventDefault();
        }}
      >
        {docRef.name}
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
