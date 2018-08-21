import React from 'react';
import PropTypes from 'prop-types';
import { compose, branch, renderNothing } from 'recompose';
import { connect } from 'react-redux';
import { DragSource } from 'react-dnd';
import { DropTarget } from 'react-dnd';

import { findItem, canMove } from 'lib/treeUtils';
import RawDocRefListingEntry from './RawDocRefListingEntry';
import withDocumentTree from 'components/FolderExplorer/withDocumentTree';
import { actionCreators as docRefListingActionCreators } from './redux';
import { actionCreators as folderExplorerActionCreators } from 'components/FolderExplorer/redux';
import ItemTypes from './dragDropTypes';

const { docRefSelectionToggled } = docRefListingActionCreators;
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
    docRefUuid,
    docRefListing: { selectedDocRefUuids, filteredDocRefs },
    keyIsDown: { Control, Meta },
  }) {
    let docRefUuids = [docRefUuid];

    // If we are dragging one of the items in a selection, bring across the entire selection
    if (selectedDocRefUuids.includes(docRefUuid)) {
      docRefUuids = selectedDocRefUuids;
    }

    return {
      docRefs: filteredDocRefs.filter(d => docRefUuids.includes(d.uuid)),
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
      { folderExplorer: { documentTree }, docRefListing, keyIsDown },
      { listingId, docRefUuid },
    ) => ({
      docRefListing: docRefListing[listingId],
      docRefWithLineage: findItem(documentTree, docRefUuid),
      keyIsDown,
    }),
    {
      docRefSelectionToggled,
      prepareDocRefCopy,
      prepareDocRefMove,
    },
  ),
  branch(({ docRefWithLineage: { node } }) => !node, renderNothing),
  DropTarget([ItemTypes.DOC_REF_UUIDS], dropTarget, dropCollect),
  DragSource(ItemTypes.DOC_REF_UUIDS, dragSource, dragCollect),
);

const DocRefListingEntry = ({
  docRefWithLineage: { node },
  listingId,
  docRefListing: { selectedDocRefUuids, inMultiSelectMode },
  onNameClick,
  includeBreadcrumb,
  docRefSelectionToggled,
  openDocRef,
  keyIsDown,
  connectDropTarget,
  connectDragSource,
  isOver,
  canDrop,
}) => {
  let className = '';
  if (selectedDocRefUuids.includes(node.uuid)) {
    className += ' doc-ref-listing__item--selected';
  }
  if (isOver) {
    className += ' dnd-over';
  }
  if (isOver) {
    if (canDrop) {
      className += ' can-drop';
    } else {
      className += ' cannot-drop';
    }
  }

  return connectDragSource(connectDropTarget(<div>
    <RawDocRefListingEntry
      className={className}
      onRowClick={() => {
            docRefSelectionToggled(listingId, node.uuid, keyIsDown);
          }}
      onNameClick={() => onNameClick(node)}
      includeBreadcrumb={includeBreadcrumb}
      openDocRef={openDocRef}
      node={node}
    />
  </div>));
};

const EnhancedDocRefListingEntry = enhance(DocRefListingEntry);

EnhancedDocRefListingEntry.propTypes = {
  listingId: PropTypes.string.isRequired,
  docRefUuid: PropTypes.string.isRequired,
  includeBreadcrumb: PropTypes.bool.isRequired,
  onNameClick: PropTypes.func.isRequired,
  openDocRef: PropTypes.func.isRequired,
};

EnhancedDocRefListingEntry.defaultProps = {
  checkedDocRefs: [],
  includeBreadcrumb: true,
};

export default EnhancedDocRefListingEntry;
