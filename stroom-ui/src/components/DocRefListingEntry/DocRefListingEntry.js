import React from 'react';
import PropTypes from 'prop-types';

import { connect } from 'react-redux';
import { compose, withHandlers, withProps } from 'recompose';

import DocRefPropType from 'lib/DocRefPropType';
import { DocRefBreadcrumb } from 'components/DocRefBreadcrumb';
import { actionCreators as selectableItemActionCreators } from 'lib/withSelectableItemListing';

const { selectionToggled } = selectableItemActionCreators;

const enhance = compose(
  connect(
    ({ keyIsDown, selectableItemListings }, { listingId, docRef }) => {
      const { selectedItems = [], focussedItem } = selectableItemListings[listingId] || {};
      const isSelected = selectedItems.map(d => d.uuid).includes(docRef.uuid);
      const inFocus = focussedItem && focussedItem.uuid === docRef.uuid;

      return {
        isSelected,
        inFocus,
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
  withProps(({ additionalClasses = [], isSelected, inFocus }) => {
    if (isSelected) {
      additionalClasses.push('selected');
    }
    if (inFocus) {
      additionalClasses.push('inFocus');
    }
    const className = `hoverable doc-ref-listing__item ${additionalClasses.join(' ')}`;

    return {
      className,
    };
  }),
);

const RawDocRefListingEntry = ({
  className, docRef, isSelected, onRowClick, onNameClick,
}) => (
  <div className={className} onClick={onRowClick}>
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
  <div className={className} onClick={onRowClick}>
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
    additionalClasses: PropTypes.array,
    isSelected: PropTypes.bool,
    openDocRef: PropTypes.func.isRequired,
  }));

export default DocRefListingEntry;

export { DocRefListingEntry, DocRefListingEntryWithBreadcrumb };
