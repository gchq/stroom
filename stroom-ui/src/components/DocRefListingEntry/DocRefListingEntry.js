import React from 'react';
import PropTypes from 'prop-types';

import { connect } from 'react-redux';
import { compose, withHandlers, withProps } from 'recompose';
import { Icon } from 'semantic-ui-react';

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
    onSelect: ({
      listingId, index, keyIsDown, selectionToggled,
    }) => (e) => {
      selectionToggled(listingId, index, keyIsDown);
      e.preventDefault();
    },
    onOpenDocRef: ({ openDocRef, docRef }) => (e) => {
      openDocRef(docRef);
      e.stopPropagation();
      e.preventDefault();
    },
    onEnterFolder: ({ openDocRef, enterFolder, docRef }) => (e) => {
      if (enterFolder) {
        enterFolder(docRef);
      } else {
        openDocRef(docRef); // fall back to this
      }
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
  className,
  docRef,
  onSelect,
  onOpenDocRef,
  onEnterFolder,
  children,
}) => (
  <div className={className} onClick={onSelect}>
    <div>
      <img
        className="stroom-icon--large"
        alt="X"
        src={require(`../../images/docRefTypes/${docRef.type}.svg`)}
      />
      <span className="doc-ref-listing__name" onClick={onOpenDocRef}>
        {docRef.name}
      </span>
      <span className="doc-ref-listing__space">&nbsp;</span>
      {docRef.type === 'System' ||
        (docRef.type === 'Folder' && (
          <Icon
            className="doc-ref-listing__icon"
            size="large"
            name="angle right"
            onClick={onEnterFolder}
          />
        ))}
    </div>
    {children}
  </div>
);

const DocRefListingEntry = enhance(RawDocRefListingEntry);

DocRefListingEntry.propTypes = {
  listingId: PropTypes.string.isRequired,
  docRef: DocRefPropType,
  index: PropTypes.number.isRequired,
  additionalClasses: PropTypes.array,
  isSelected: PropTypes.bool,
  openDocRef: PropTypes.func.isRequired,
  enterFolder: PropTypes.func,
};

export default DocRefListingEntry;
