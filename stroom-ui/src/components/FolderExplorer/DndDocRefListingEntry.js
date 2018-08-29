import React from 'react';
import PropTypes from 'prop-types';
import { compose, branch, renderNothing } from 'recompose';
import { connect } from 'react-redux';
import { DragSource } from 'react-dnd';
import { DropTarget } from 'react-dnd';

import { findItem, canMove } from 'lib/treeUtils';
import { DocRefListingEntry } from 'components/DocRefListingEntry';
import withDocumentTree from 'components/FolderExplorer/withDocumentTree';
import { actionCreators as folderExplorerActionCreators } from 'components/FolderExplorer/redux';
import ItemTypes from './dragDropTypes';

const { prepareDocRefCopy, prepareDocRefMove } = folderExplorerActionCreators;

const dropTarget = {
  canDrop(
    {
      docRefWithLineage: { node },
    },
    monitor,
  ) {
    const { docRefs } = monitor.getItem();

    return (
      !!node &&
      node.type === 'Folder' &&
      docRefs.reduce((acc, curr) => acc && canMove(curr, node), true)
    );
  },
  drop(
    {
      prepareDocRefCopy,
      prepareDocRefMove,
      docRefWithLineage: { node },
    },
    monitor,
  ) {
    const { docRefs, isCopy } = monitor.getItem();
    const docRefUuids = docRefs.map(d => d.uuid);

    if (isCopy) {
      prepareDocRefCopy(docRefUuids, node.uuid);
    } else {
      prepareDocRefMove(docRefUuids, node.uuid);
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
    docRefWithLineage: {node: docRef},
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
  withDocumentTree,
  connect(
    (
      { folderExplorer: { documentTree }, selectableItemListings, keyIsDown },
      { listingId, docRefUuid },
    ) => ({
      selectableItemListing: selectableItemListings[listingId],
      docRefWithLineage: findItem(documentTree, docRefUuid),
      keyIsDown,
    }),
    {
      prepareDocRefCopy,
      prepareDocRefMove,
    },
  ),
  branch(({ docRefWithLineage: { node } }) => !node, renderNothing),
  DropTarget([ItemTypes.DOC_REF_UUIDS], dropTarget, dropCollect),
  DragSource(ItemTypes.DOC_REF_UUIDS, dragSource, dragCollect),
);

const DndDocRefListingEntry = ({
  docRefWithLineage: { node },
  index,
  listingId,
  selectableItemListing: { selectedItems },
  onNameClick,
  openDocRef,
  connectDropTarget,
  connectDragSource,
  isOver,
  canDrop,
}) => {
  let additionalClasses = [];
  if (isOver) {
    additionalClasses.push('dnd-over');
  }
  if (isOver) {
    if (canDrop) {
      additionalClasses.push('can-drop');
    } else {
      additionalClasses.push('cannot-drop');
    }
  }

  return connectDragSource(connectDropTarget(<div>
    <DocRefListingEntry
      additionalClasses={additionalClasses}
      index={index}
      openDocRef={openDocRef}
      docRef={node}
      listingId={listingId}
    />
  </div>));
};

const EnhancedDocRefListingEntry = enhance(DndDocRefListingEntry);

EnhancedDocRefListingEntry.propTypes = {
  index: PropTypes.number.isRequired,
  listingId: PropTypes.string.isRequired,
  docRefUuid: PropTypes.string.isRequired,
  onNameClick: PropTypes.func.isRequired,
  openDocRef: PropTypes.func.isRequired,
};

EnhancedDocRefListingEntry.defaultProps = {
  checkedDocRefs: [],
};

export default EnhancedDocRefListingEntry;
