import React from 'react';
import PropTypes from 'prop-types';
import { compose } from 'recompose';
import { connect } from 'react-redux';
import { DragSource } from 'react-dnd';
import { DropTarget } from 'react-dnd';

import DocRefPropType from 'lib/DocRefPropType';
import { findItem, canMove } from 'lib/treeUtils';
import DocRefListingEntry from 'components/DocRefListingEntry';
import { actionCreators as folderExplorerActionCreators } from 'components/FolderExplorer/redux';
import ItemTypes from './dragDropTypes';

const { prepareDocRefCopy, prepareDocRefMove } = folderExplorerActionCreators;

const dropTarget = {
  canDrop(
    {
      docRef,
    },
    monitor,
  ) {
    const { docRefs } = monitor.getItem();

    return (
      !!docRef &&
      docRef.type === 'Folder' &&
      docRefs.reduce((acc, curr) => acc && canMove(curr, docRef), true)
    );
  },
  drop(
    {
      listingId,
      prepareDocRefCopy,
      prepareDocRefMove,
      docRef,
    },
    monitor,
  ) {
    const { docRefs, isCopy } = monitor.getItem();
    const docRefUuids = docRefs.map(d => d.uuid);

    if (isCopy) {
      prepareDocRefCopy(listingId, docRefUuids, docRef.uuid);
    } else {
      prepareDocRefMove(listingId, docRefUuids, docRef.uuid);
    }
  },
};

function dropCollect(connect, monitor) {
  return {
    connectDropTarget: connect.dropTarget(),
    isOver: monitor.isOver(),
    canDrop: monitor.canDrop(),
  };
}

const dragSource = {
  canDrag(props) {
    return true;
  },
  beginDrag({
    docRef,
    selectableItemListing: { selectedItems },
    keyIsDown: { Control, Meta },
  }) {
    let docRefs = [docRef];

    // If we are dragging one of the items in a selection, bring across the entire selection
    let selectedDocRefUuids = selectedItems.map(d => d.uuid);
    if (selectedDocRefUuids.includes(docRef.uuid)) {
      docRefs = selectedItems;
    }

    return {
      docRefs,
      isCopy: !!(Control || Meta),
    };
  },
};

function dragCollect(connect, monitor) {
  return {
    connectDragSource: connect.dragSource(),
    isDragging: monitor.isDragging(),
  };
}

const enhance = compose(
  connect(
    (
      { selectableItemListings, keyIsDown },
      { listingId },
    ) => ({
      selectableItemListing: selectableItemListings[listingId],
      keyIsDown,
    }),
    {
      prepareDocRefCopy,
      prepareDocRefMove,
    },
  ),
  DropTarget([ItemTypes.DOC_REF_UUIDS], dropTarget, dropCollect),
  DragSource(ItemTypes.DOC_REF_UUIDS, dragSource, dragCollect),
);

let DndDocRefListingEntry = ({
  docRef,
  listingId,
  openDocRef,
  connectDropTarget,
  connectDragSource,
  isOver,
  canDrop,
}) => connectDragSource(connectDropTarget(<div>
    <DocRefListingEntry
      dndIsOver={isOver}
      dndCanDrop={canDrop}
      openDocRef={openDocRef}
      docRef={docRef}
      listingId={listingId}
    />
  </div>));

DndDocRefListingEntry = enhance(DndDocRefListingEntry);

DndDocRefListingEntry.propTypes = {
  listingId: PropTypes.string.isRequired,
  docRef: DocRefPropType,
  onNameClick: PropTypes.func.isRequired,
  openDocRef: PropTypes.func.isRequired,
};

DndDocRefListingEntry.defaultProps = {
  checkedDocRefs: [],
};

export default DndDocRefListingEntry;
