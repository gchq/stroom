import * as React from "react";
import { pipe } from "ramda";
import {
  DragSource,
  DropTarget,
  DropTargetSpec,
  DragSourceSpec,
  DropTargetCollector,
  DragSourceCollector,
} from "react-dnd";

import { canMove } from "lib/treeUtils/treeUtils";
import DocRefListingEntry from "../../DocRefListingEntry";
import { Props as DocRefListingEntryProps } from "../../DocRefListingEntry/types";
import {
  DragDropTypes,
  DragCollectedProps,
  DropCollectedProps,
  DragObject,
} from "./types";
import { KeyDownState } from "lib/useKeyIsDown";
import { DocRefType } from "components/DocumentEditors/useDocumentApi/types/base";

interface Props extends DocRefListingEntryProps {
  keyIsDown: KeyDownState;
  showCopyDialog: (docRefUuids: string[], destination: DocRefType) => void;
  showMoveDialog: (docRefUuids: string[], destination: DocRefType) => void;
}

interface EnhancedProps extends Props, DragCollectedProps, DropCollectedProps {}

const dropTarget: DropTargetSpec<Props> = {
  canDrop({ docRef }, monitor) {
    const { docRefs } = monitor.getItem();

    return (
      !!docRef &&
      docRef.type === "Folder" &&
      docRefs.reduce(
        (acc: boolean, curr: DocRefType) => acc && canMove(curr, docRef),
        true,
      )
    );
  },
  drop({ docRef }, monitor) {
    const {
      docRefs,
      isCopy,
      showCopyDialog,
      showMoveDialog,
    } = monitor.getItem();
    const docRefUuids = docRefs.map((d: DocRefType) => d.uuid);

    if (isCopy) {
      showCopyDialog(docRefUuids, docRef.uuid);
    } else {
      showMoveDialog(docRefUuids, docRef.uuid);
    }
  },
};

let dropCollect: DropTargetCollector<
  DropCollectedProps,
  Props
> = function dropCollect(connect, monitor) {
  return {
    connectDropTarget: connect.dropTarget(),
    isOver: monitor.isOver(),
    canDrop: monitor.canDrop(),
  };
};

const dragSource: DragSourceSpec<Props, DragObject> = {
  canDrag() {
    return true;
  },
  beginDrag({ docRef, selectedDocRefs, keyIsDown: { Control, Meta } }) {
    let docRefs = [docRef];

    // If we are dragging one of the items in a selection, bring across the entire selection
    let selectedDocRefUuids = selectedDocRefs.map(d => d.uuid);
    if (selectedDocRefUuids.includes(docRef.uuid)) {
      docRefs = selectedDocRefs;
    }

    return {
      docRefs,
      isCopy: !!(Control || Meta),
    };
  },
};

const dragCollect: DragSourceCollector<
  DragCollectedProps,
  Props
> = function dragCollect(connect, monitor) {
  return {
    connectDragSource: connect.dragSource(),
    isDragging: monitor.isDragging(),
  };
};

// dnd_error: temporarily disable dnd-related code to get the build working
//const enhance = pipe(
/* DropTarget([DragDropTypes.DOC_REF_UUIDS], dropTarget, dropCollect), */
/* DragSource(DragDropTypes.DOC_REF_UUIDS, dragSource, dragCollect), */
/* ); */

let DndDocRefListingEntry = ({
  connectDragSource,
  connectDropTarget,
  ...rest
}: EnhancedProps) =>
  connectDragSource(
    connectDropTarget(
      <div>
        <DocRefListingEntry {...rest} />
      </div>,
    ),
  );

// dnd_error: temporarily disable dnd-related code to get the build working
/* export default enhance(DndDocRefListingEntry); */
export default DndDocRefListingEntry;
