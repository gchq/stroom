import React from 'react';
import PropTypes from 'prop-types';

import { connect } from 'react-redux';
import { compose, withHandlers, withProps } from 'recompose';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import DocRefImage from 'components/DocRefImage';
import DocRefPropType from 'lib/DocRefPropType';
import {
  actionCreators as selectableItemActionCreators,
  defaultSelectableItemListingState,
} from 'lib/withSelectableItemListing';

const { selectionToggled } = selectableItemActionCreators;

const enhance = compose(
  connect(
    ({ keyIsDown, selectableItemListings }, { listingId, docRef }) => {
      const { selectedItems = [], focussedItem } =
        selectableItemListings[listingId] || defaultSelectableItemListingState;
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
      listingId, docRef, keyIsDown, selectionToggled,
    }) => (e) => {
      selectionToggled(listingId, docRef.uuid, keyIsDown);
      e.preventDefault();
      e.stopPropagation();
    },
    onOpenDocRef: ({ openDocRef, docRef }) => (e) => {
      openDocRef(docRef);
      e.preventDefault();
      e.stopPropagation();
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
  withProps(({
    dndIsOver, dndCanDrop, isSelected, inFocus,
  }) => {
    const additionalClasses = [];
    additionalClasses.push('DocRefListingEntry');
    additionalClasses.push('hoverable');

    if (dndIsOver) {
      additionalClasses.push('dnd-over');
    }
    if (dndIsOver) {
      if (dndCanDrop) {
        additionalClasses.push('can-drop');
      } else {
        additionalClasses.push('cannot-drop');
      }
    }

    if (isSelected) {
      additionalClasses.push('selected');
    }
    if (inFocus) {
      additionalClasses.push('inFocus');
    }

    return {
      className: additionalClasses.join(' '),
    };
  }),
);

let DocRefListingEntry = ({
  className, docRef, onSelect, onOpenDocRef, onEnterFolder,
}) => (
  <div className={className} onClick={onSelect}>
    <DocRefImage docRefType={docRef.type} />
    <div className="DocRefListingEntry__name" onClick={onOpenDocRef}>
      {docRef.name}
    </div>
    <div className="DocRefListingEntry__space" />
    {docRef.type === 'System' ||
      (docRef.type === 'Folder' && (
        <FontAwesomeIcon
          className="DocRefListingEntry__icon"
          size="lg"
          icon="angle-right"
          onClick={onEnterFolder}
        />
      ))}
  </div>
);

DocRefListingEntry = enhance(DocRefListingEntry);

DocRefListingEntry.propTypes = {
  listingId: PropTypes.string.isRequired,
  docRef: DocRefPropType,
  isSelected: PropTypes.bool,
  dndIsOver: PropTypes.bool,
  dndCanDrop: PropTypes.bool,
  openDocRef: PropTypes.func.isRequired,
  enterFolder: PropTypes.func,
};

export default DocRefListingEntry;
