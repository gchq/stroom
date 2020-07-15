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

import DocRefEditor, { useDocRefEditor } from "../DocRefEditor";
import { SwitchedDocRefEditorProps } from "../DocRefEditor/types";
import Loader from "../../Loader";
/* import DndDocRefListingEntry from "./DndDocRefListingEntry"; */
import CreateDocRefDialog, {
  useDialog as useCreateDialog,
} from "./CreateDocRefDialog/CreateDocRefDialog";
import {
  CopyMoveDocRefDialog,
  useDialog as useCopyMoveDialog,
} from "./CopyMoveDocRefDialog/CopyMoveDocRefDialog";
import RenameDocRefDialog, {
  useDialog as useRenameDialog,
} from "./RenameDocRefDialog/RenameDocRefDialog";
import DeleteDocRefDialog, {
  useDialog as useDeleteDialog,
} from "./DeleteDocRefDialog/DeleteDocRefDialog";
import DocRefInfoModal from "../../DocRefInfoModal";
import useSelectableItemListing, {
  SelectionBehaviour,
} from "lib/useSelectableItemListing";
import { useDocRefInfoDialog } from "../../DocRefInfoModal/DocRefInfoModal";
import { useDocumentTree } from "components/DocumentEditors/api/explorer";
import { useAppNavigation } from "lib/useAppNavigation";
import { ButtonProps } from "components/Button/types";
/* import useKeyIsDown from "lib/useKeyIsDown"; */

const FolderExplorer: React.FunctionComponent<SwitchedDocRefEditorProps> = ({
  docRefUuid,
}) => {
  const {
    findDocRefWithLineage,
    createDocument,
    copyDocuments,
    moveDocuments,
    renameDocument,
    deleteDocuments,
  } = useDocumentTree();

  const {
    nav: { goToEditDocRef },
  } = useAppNavigation();
  const folder = React.useMemo(() => findDocRefWithLineage(docRefUuid), [
    findDocRefWithLineage,
    docRefUuid,
  ]);

  const onCreateDocument = React.useCallback(
    (docRefType: string, docRefName: string, permissionInheritance: string) => {
      if (!!folder) {
        createDocument(
          docRefType,
          docRefName,
          folder.node,
          permissionInheritance,
        );
      }
    },
    [createDocument, folder],
  );

  const goBack = React.useCallback(() => {
    if (!!folder) {
      if (folder.lineage.length > 0) {
        goToEditDocRef(folder.lineage[folder.lineage.length - 1]);
      }
    }
  }, [folder, goToEditDocRef]);

  const {
    showDialog: showDeleteDialog,
    componentProps: deleteDialogComponentProps,
  } = useDeleteDialog(deleteDocuments);
  const {
    showDialog: showCopyDialog,
    componentProps: copyDialogComponentProps,
  } = useCopyMoveDialog(copyDocuments);
  const {
    showDialog: showMoveDialog,
    componentProps: moveDialogComponentProps,
  } = useCopyMoveDialog(moveDocuments);
  const {
    showDialog: showRenameDialog,
    componentProps: renameDialogComponentProps,
  } = useRenameDialog(renameDocument);
  const {
    showDialog: showDocRefInfoDialog,
    componentProps: docRefInfoDialogComponentProps,
  } = useDocRefInfoDialog();
  const {
    showDialog: showCreateDialog,
    componentProps: createDialogComponentProps,
  } = useCreateDialog(onCreateDocument);
  const {
    /* onKeyDown, */
    selectedItems: selectedDocRefs,
    /* toggleSelection, */
  } = useSelectableItemListing({
    items: folder.node.children || [],
    selectionBehaviour: SelectionBehaviour.MULTIPLE,
    getKey: React.useCallback((d) => d.uuid, []),
    openItem: goToEditDocRef,
    goBack: goBack,
  });
  /* const keyIsDown = useKeyIsDown(); */

  const onClickCreate = React.useCallback(() => {
    if (!!folder) {
      showCreateDialog();
    }
  }, [folder, showCreateDialog]);

  const additionalActionBarItems: ButtonProps[] = [
    {
      icon: "file",
      onClick: onClickCreate,
      title: "Create a Document",
      text: "Create",
    },
  ];

  const singleSelectedDocRef =
    selectedDocRefs.length === 1 ? selectedDocRefs[0] : undefined;
  const selectedDocRefUuids = selectedDocRefs.map((d) => d.uuid);

  if (selectedDocRefs.length > 0) {
    if (singleSelectedDocRef) {
      additionalActionBarItems.push({
        icon: "info",
        text: "Info",
        onClick: () => showDocRefInfoDialog(singleSelectedDocRef),
        title: "View Information about this document",
      });
      additionalActionBarItems.push({
        icon: "edit",
        text: "Rename",
        onClick: () => showRenameDialog(singleSelectedDocRef),
        title: "Rename this document",
      });
    }
    additionalActionBarItems.push({
      icon: "copy",
      text: "Copy",
      onClick: () => showCopyDialog(selectedDocRefUuids),
      title: "Copy selected documents",
    });
    additionalActionBarItems.push({
      icon: "arrows-alt",
      text: "Move",
      onClick: () => showMoveDialog(selectedDocRefUuids),
      title: "Move selected documents",
    });
    additionalActionBarItems.push({
      icon: "trash",
      text: "Delete",
      onClick: () => showDeleteDialog(selectedDocRefUuids),
      title: "Delete selected documents",
    });
  }

  const { editorProps: folderEditorProps } = useDocRefEditor({
    docRefUuid,
  });

  if (!folder) {
    return <Loader message="Loading folder..." />;
  }

  return (
    <DocRefEditor
      {...folderEditorProps}
      additionalActionBarItems={additionalActionBarItems}
    >
      {/* dnd_error: temporarily disable dnd-related code to get the build working */}
      {/* <div
            tabIndex={0}
            className="DocRefEditor__focusarea"
            onKeyDown={onKeyDown}
            >
            {folder &&
            folder.node &&
            folder.node.children &&
            folder.node.children.map(docRef => (
            <DndDocRefListingEntry
            key={docRef.uuid}
            docRef={docRef}
            openDocRef={goToEditDocRef}
            keyIsDown={keyIsDown}
            showCopyDialog={showCopyDialog}
            showMoveDialog={showMoveDialog}
            selectedDocRefs={selectedDocRefs}
            toggleSelection={toggleSelection}
            />
            ))}
            </div> */}
      <DocRefInfoModal {...docRefInfoDialogComponentProps} />
      <CopyMoveDocRefDialog {...moveDialogComponentProps} />
      <RenameDocRefDialog {...renameDialogComponentProps} />
      <DeleteDocRefDialog {...deleteDialogComponentProps} />
      <CopyMoveDocRefDialog {...copyDialogComponentProps} />
      <CreateDocRefDialog {...createDialogComponentProps} />
    </DocRefEditor>
  );
};

export default FolderExplorer;
