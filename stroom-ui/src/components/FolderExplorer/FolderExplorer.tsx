/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import * as React from "react";
import { useEffect } from "react";
import { connect } from "react-redux";
import { compose } from "recompose";
import { withRouter, RouteComponentProps } from "react-router-dom";

import DocRefEditor from "../DocRefEditor";
import Loader from "../Loader";
import { findItem } from "../../lib/treeUtils";
import { copyDocuments, moveDocuments } from "./explorerClient";
import DndDocRefListingEntry from "./DndDocRefListingEntry";
import CreateDocRefDialog, {
  useDialog as useCreateDialog
} from "./CreateDocRefDialog";
import CopyMoveDocRefDialog, {
  useDialog as useCopyMoveDialog
} from "./CopyMoveDocRefDialog";
import RenameDocRefDialog, {
  useDialog as useRenameDialog
} from "./RenameDocRefDialog";
import DeleteDocRefDialog, {
  useDialog as useDeleteDialog
} from "./DeleteDocRefDialog";
import DocRefInfoModal from "../DocRefInfoModal";
import { fetchDocTree } from "../FolderExplorer/explorerClient";
import { Props as ButtonProps } from "../Button";
import { DocRefType, DocRefWithLineage, DocRefConsumer } from "../../types";
import { GlobalStoreState } from "../../startup/reducers";
import useSelectableItemListing, {
  SelectionBehaviour
} from "../../lib/useSelectableItemListing";
import { useDocRefInfoDialog } from "../DocRefInfoModal/DocRefInfoModal";

export interface Props {
  folderUuid: string;
}

interface ConnectState {
  folder: DocRefWithLineage;
}

interface ConnectDispatch {
  copyDocuments: typeof copyDocuments;
  moveDocuments: typeof moveDocuments;
  fetchDocTree: typeof fetchDocTree;
}

export interface EnhancedProps
  extends Props,
    RouteComponentProps,
    ConnectState,
    ConnectDispatch {}

const enhance = compose<EnhancedProps, Props>(
  withRouter,
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ folderExplorer: { documentTree } }, { folderUuid }) => ({
      folder: findItem(documentTree, folderUuid)!
    }),
    {
      copyDocuments,
      moveDocuments,
      fetchDocTree
    }
  )
);

const FolderExplorer = ({
  folder,
  folderUuid,
  copyDocuments,
  moveDocuments,
  history,
  fetchDocTree
}: EnhancedProps) => {
  const openDocRef: DocRefConsumer = (d: DocRefType) =>
    history.push(`/s/doc/${d.type}/${d.uuid}`);

  const {
    showDialog: showDeleteDialog,
    componentProps: deleteDialogComponentProps
  } = useDeleteDialog();
  const {
    showDialog: showCopyDialog,
    componentProps: copyDialogComponentProps
  } = useCopyMoveDialog(copyDocuments);
  const {
    showDialog: showMoveDialog,
    componentProps: moveDialogComponentProps
  } = useCopyMoveDialog(moveDocuments);
  const {
    showDialog: showRenameDialog,
    componentProps: renameDialogComponentProps
  } = useRenameDialog();
  const {
    showDialog: showDocRefInfoDialog,
    componentProps: docRefInfoDialogComponentProps
  } = useDocRefInfoDialog();
  const {
    showDialog: showCreateDialog,
    componentProps: createDialogComponentProps
  } = useCreateDialog();
  const {
    onKeyDownWithShortcuts,
    selectedItems: selectedDocRefs,
    selectionToggled,
    keyIsDown
  } = useSelectableItemListing({
    items: folder.node.children || [],
    selectionBehaviour: SelectionBehaviour.MULTIPLE,
    getKey: d => d.uuid,
    openItem: openDocRef,
    goBack: () => {
      if (lineage.length > 0) {
        openDocRef(lineage[lineage.length - 1]);
      }
    }
  });
  useEffect(() => {
    fetchDocTree();
  });

  if (!folder) {
    return <Loader message="Loading folder..." />;
  }

  const { node, lineage } = folder;

  const actionBarItems: Array<ButtonProps> = [
    {
      icon: "file",
      onClick: () => showCreateDialog(node),
      title: "Create a Document",
      text: "Create"
    }
  ];

  const singleSelectedDocRef =
    selectedDocRefs.length === 1 ? selectedDocRefs[0] : undefined;
  const selectedDocRefUuids = selectedDocRefs.map(d => d.uuid);

  if (selectedDocRefs.length > 0) {
    if (singleSelectedDocRef) {
      actionBarItems.push({
        icon: "info",
        text: "Info",
        onClick: () => showDocRefInfoDialog(singleSelectedDocRef),
        title: "View Information about this document"
      });
      actionBarItems.push({
        icon: "edit",
        text: "Rename",
        onClick: () => showRenameDialog(singleSelectedDocRef),
        title: "Rename this document"
      });
    }
    actionBarItems.push({
      icon: "copy",
      text: "Copy",
      onClick: () => showCopyDialog(selectedDocRefUuids),
      title: "Copy selected documents"
    });
    actionBarItems.push({
      icon: "arrows-alt",
      text: "Move",
      onClick: () => showMoveDialog(selectedDocRefUuids),
      title: "Move selected documents"
    });
    actionBarItems.push({
      icon: "trash",
      text: "Delete",
      onClick: () => showDeleteDialog(selectedDocRefUuids),
      title: "Delete selected documents"
    });
  }

  return (
    <DocRefEditor docRefUuid={folderUuid} actionBarItems={actionBarItems}>
      <div tabIndex={0} onKeyDown={onKeyDownWithShortcuts}>
        {node &&
          node.children &&
          node.children.map(docRef => (
            <DndDocRefListingEntry
              key={docRef.uuid}
              docRef={docRef}
              openDocRef={openDocRef}
              keyIsDown={keyIsDown}
              showCopyDialog={showCopyDialog}
              showMoveDialog={showMoveDialog}
              selectedDocRefs={selectedDocRefs}
              selectionToggled={selectionToggled}
            />
          ))}
      </div>
      <DocRefInfoModal {...docRefInfoDialogComponentProps} />
      <CopyMoveDocRefDialog {...moveDialogComponentProps} />
      <RenameDocRefDialog {...renameDialogComponentProps} />
      <DeleteDocRefDialog {...deleteDialogComponentProps} />
      <CopyMoveDocRefDialog {...copyDialogComponentProps} />
      <CreateDocRefDialog {...createDialogComponentProps} />
    </DocRefEditor>
  );
};

export default enhance(FolderExplorer);
