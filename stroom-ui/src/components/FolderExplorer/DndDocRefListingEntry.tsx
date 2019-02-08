import * as React from "react";
import { compose } from "recompose";
import { connect } from "react-redux";
import {
  DragSource,
  DropTarget,
  DropTargetSpec,
  DragSourceSpec,
  DropTargetCollector,
  DragSourceCollector
} from "react-dnd";

import { canMove } from "../../lib/treeUtils";
import DocRefListingEntry from "../DocRefListingEntry";
import {
  DragDropTypes,
  DragCollectedProps,
  DropCollectedProps,
  DragObject
} from "./dragDropTypes";
import { moveDocuments, copyDocuments } from "./explorerClient";
import { DocRefType, DocRefConsumer } from "../../types";
import { StoreStatePerId as SelectableItemListingState } from "../../lib/withSelectableItemListing";
import { GlobalStoreState } from "../../startup/reducers";
import { StoreState as KeyIsDownStoreState } from "../../lib/KeyIsDown";

export interface Props {
  listingId: string;
  docRef: DocRefType;
  onNameClick: DocRefConsumer;
  openDocRef: DocRefConsumer;
}

interface ConnectDispatch {
  moveDocuments: typeof moveDocuments;
  copyDocuments: typeof copyDocuments;
}

interface ConnectState {
  selectableItemListing: SelectableItemListingState;
  keyIsDown: KeyIsDownStoreState;
}

export interface DndProps extends Props, ConnectDispatch, ConnectState {}

export interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    DragCollectedProps,
    DropCollectedProps {}

const dropTarget: DropTargetSpec<DndProps> = {
  canDrop({ docRef }, monitor) {
    const { docRefs } = monitor.getItem();

    return (
      !!docRef &&
      docRef.type === "Folder" &&
      docRefs.reduce(
        (acc: boolean, curr: DocRefType) => acc && canMove(curr, docRef),
        true
      )
    );
  },
  drop({ docRef }, monitor) {
    const { docRefs, isCopy } = monitor.getItem();
    // TODO - Copy/Move Documents in App Chrome
    console.log("Copy", { docRefs, isCopy, docRef });
    //const docRefUuids = docRefs.map((d: DocRefType) => d.uuid);

    if (isCopy) {
      //prepareDocRefCopy(listingId, docRefUuids, docRef.uuid);
    } else {
      //prepareDocRefMove(listingId, docRefUuids, docRef.uuid);
    }
  }
};

let dropCollect: DropTargetCollector<DropCollectedProps> = function dropCollect(
  connect,
  monitor
) {
  return {
    connectDropTarget: connect.dropTarget(),
    isOver: monitor.isOver(),
    canDrop: monitor.canDrop()
  };
};

const dragSource: DragSourceSpec<DndProps, DragObject> = {
  canDrag(props) {
    return true;
  },
  beginDrag({
    docRef,
    selectableItemListing: { selectedItems },
    keyIsDown: { Control, Meta }
  }) {
    let docRefs = [docRef];

    // If we are dragging one of the items in a selection, bring across the entire selection
    let selectedDocRefUuids = selectedItems.map(d => d.uuid);
    if (selectedDocRefUuids.includes(docRef.uuid)) {
      docRefs = selectedItems;
    }

    return {
      docRefs,
      isCopy: !!(Control || Meta)
    };
  }
};

const dragCollect: DragSourceCollector<
  DragCollectedProps
> = function dragCollect(connect, monitor) {
  return {
    connectDragSource: connect.dragSource(),
    isDragging: monitor.isDragging()
  };
};

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ selectableItemListings, keyIsDown }, { listingId }) => ({
      selectableItemListing: selectableItemListings[listingId],
      keyIsDown
    }),
    {
      copyDocuments,
      moveDocuments
    }
  ),
  DropTarget([DragDropTypes.DOC_REF_UUIDS], dropTarget, dropCollect),
  DragSource(DragDropTypes.DOC_REF_UUIDS, dragSource, dragCollect)
);

let DndDocRefListingEntry = ({
  docRef,
  listingId,
  openDocRef,
  connectDropTarget,
  connectDragSource,
  isOver,
  canDrop
}: EnhancedProps) =>
  connectDragSource(
    connectDropTarget(
      <div>
        <DocRefListingEntry
          dndIsOver={isOver}
          dndCanDrop={canDrop}
          openDocRef={openDocRef}
          docRef={docRef}
          listingId={listingId}
        />
      </div>
    )
  );

export default enhance(DndDocRefListingEntry);
