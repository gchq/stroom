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
import { connect } from "react-redux";
import { compose, branch, renderComponent, withHandlers } from "recompose";
import { withRouter } from "react-router-dom";

import DocRefEditor from "../DocRefEditor";
import Loader from "../Loader";
import { findItem } from "../../lib/treeUtils";
import { fetchDocInfo, copyDocuments, moveDocuments } from "./explorerClient";
import DndDocRefListingEntry from "./DndDocRefListingEntry";
import CreateDocRefDialog, {
  useCreateDocRefDialog
} from "./CreateDocRefDialog";
import CopyMoveDocRefDialog, {
  useCopyDocRefDialog
} from "./CopyMoveDocRefDialog";
import RenameDocRefDialog, {
  useRenameDocRefDialog
} from "./RenameDocRefDialog";
import DeleteDocRefDialog, {
  useDeleteDocRefDialog
} from "./DeleteDocRefDialog";
import DocRefInfoModal from "../DocRefInfoModal";
import withDocumentTree, {
  EnhancedProps as WithDocumentTreeProps
} from "./withDocumentTree";
import withSelectableItemListing, {
  SelectionBehaviour,
  StoreStatePerId as SelectableItemListingStatePerId,
  EnhancedProps as SelectableItemListingProps,
  defaultStatePerId as defaultSelectableItemListing
} from "../../lib/withSelectableItemListing";
import { Props as ButtonProps } from "../Button";
import { DocRefConsumer, DocRefType, DocRefWithLineage } from "../../types";
import { GlobalStoreState } from "../../startup/reducers";

const LISTING_ID = "folder-explorer";

export interface Props {
  folderUuid: string;
}

interface ConnectState {
  folder: DocRefWithLineage;
  selectableItemListing: SelectableItemListingStatePerId;
}

interface WithHandlers {
  openDocRef: DocRefConsumer;
}

interface ConnectDispatch {
  fetchDocInfo: typeof fetchDocInfo;
  copyDocuments: typeof copyDocuments;
  moveDocuments: typeof moveDocuments;
}

export interface EnhancedProps
  extends Props,
    WithDocumentTreeProps,
    WithHandlers,
    ConnectState,
    ConnectDispatch,
    SelectableItemListingProps<DocRefType> {}

const enhance = compose<EnhancedProps, Props>(
  withDocumentTree,
  withRouter,
  withHandlers({
    openDocRef: ({ history }) => (d: DocRefType) =>
      history.push(`/s/doc/${d.type}/${d.uuid}`)
  }),
  connect<
    ConnectState,
    ConnectDispatch,
    Props & WithDocumentTreeProps,
    GlobalStoreState
  >(
    ({ documentTree, selectableItemListings }, { folderUuid }) => ({
      folder: findItem(documentTree, folderUuid)!,
      selectableItemListing:
        selectableItemListings[LISTING_ID] || defaultSelectableItemListing
    }),
    {
      copyDocuments,
      moveDocuments,
      fetchDocInfo
    }
  ),
  branch(
    ({ folder }) => !folder,
    renderComponent(() => <Loader message="Loading folder..." />)
  ),
  withSelectableItemListing<DocRefType>(
    ({
      openDocRef,
      folder: {
        lineage,
        node: { children }
      }
    }) => ({
      listingId: LISTING_ID,
      items: children,
      selectionBehaviour: SelectionBehaviour.MULTIPLE,
      getKey: d => d.uuid,
      openItem: openDocRef,
      goBack: () => {
        if (lineage.length > 0) {
          openDocRef(lineage[lineage.length - 1]);
        }
      }
    })
  )
);

const FolderExplorer = ({
  folder: { node },
  folderUuid,
  folder,
  copyDocuments,
  moveDocuments,
  fetchDocInfo,
  selectableItemListing: { selectedItems },
  onKeyDownWithShortcuts,
  openDocRef
}: EnhancedProps) => {
  const {
    showDeleteDialog,
    componentProps: deleteDialogComponentProps
  } = useDeleteDocRefDialog();
  const {
    showDialog: showCopyDialog,
    componentProps: copyDialogComponentProps
  } = useCopyDocRefDialog(copyDocuments);
  const {
    showDialog: showMoveDialog,
    componentProps: moveDialogComponentProps
  } = useCopyDocRefDialog(moveDocuments);
  const {
    showRenameDialog,
    componentProps: renameDialogComponentProps
  } = useRenameDocRefDialog();
  const {
    showCreateDialog,
    componentProps: createDialogComponentProps
  } = useCreateDocRefDialog();

  const actionBarItems: Array<ButtonProps> = [
    {
      icon: "file",
      onClick: () => showCreateDialog(folder.node),
      title: "Create a Document",
      text: "Create"
    }
  ];

  const singleSelectedDocRef =
    selectedItems.length === 1 ? selectedItems[0] : undefined;
  const selectedDocRefUuids = selectedItems.map((d: DocRefType) => d.uuid);

  if (selectedItems.length > 0) {
    if (singleSelectedDocRef) {
      actionBarItems.push({
        icon: "info",
        text: "Info",
        onClick: () => fetchDocInfo(singleSelectedDocRef),
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
              listingId={LISTING_ID}
              docRef={docRef}
              onNameClick={openDocRef}
              openDocRef={openDocRef}
            />
          ))}
      </div>
      <DocRefInfoModal />
      <CopyMoveDocRefDialog {...moveDialogComponentProps} />
      <RenameDocRefDialog {...renameDialogComponentProps} />
      <DeleteDocRefDialog {...deleteDialogComponentProps} />
      <CopyMoveDocRefDialog {...copyDialogComponentProps} />
      <CreateDocRefDialog {...createDialogComponentProps} />
    </DocRefEditor>
  );
};

export default enhance(FolderExplorer);
