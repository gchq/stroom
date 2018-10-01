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
import {
  compose,
  withProps,
  branch,
  renderComponent,
  withHandlers
} from "recompose";
import { withRouter } from "react-router-dom";

import DocRefEditor from "../DocRefEditor";
import Loader from "../Loader";
import { findItem } from "../../lib/treeUtils";
import { actionCreators } from "./redux";
import { fetchDocInfo } from "../FolderExplorer/explorerClient";
import DndDocRefListingEntry from "./DndDocRefListingEntry";
import NewDocRefDialog from "./NewDocRefDialog";
import CopyDocRefDialog from "./CopyDocRefDialog";
import MoveDocRefDialog from "./MoveDocRefDialog";
import RenameDocRefDialog from "./RenameDocRefDialog";
import DeleteDocRefDialog from "./DeleteDocRefDialog";
import DocRefInfoModal from "../DocRefInfoModal";
import withDocumentTree, {
  EnhancedProps as WithDocumentTreeProps
} from "./withDocumentTree";
import withSelectableItemListing, {
  SelectionBehaviour,
  StoreState as SelectableItemListingState,
  LifecycleProps as SelectableItemListingProps,
  defaultStatePerId as defaultSelectableItemListing
} from "../../lib/withSelectableItemListing";
import { Props as ButtonProps } from "../Button";
import { DocRefConsumer, DocRefType, DocRefWithLineage } from "../../types";
import { GlobalStoreState } from "../../startup/reducers";

const {
  prepareDocRefCreation,
  prepareDocRefDelete,
  prepareDocRefCopy,
  prepareDocRefRename,
  prepareDocRefMove
} = actionCreators;

const LISTING_ID = "folder-explorer";

export interface Props {
  folderUuid: string;
}

interface ConnectState {
  folder: DocRefWithLineage;
  selectableItemListing: SelectableItemListingState;
}

interface WithHandlers {
  openDocRef: DocRefConsumer;
}

interface ConnectDispatch {
  prepareDocRefCreation: typeof prepareDocRefCreation;
  prepareDocRefDelete: typeof prepareDocRefDelete;
  prepareDocRefCopy: typeof prepareDocRefCopy;
  prepareDocRefRename: typeof prepareDocRefRename;
  prepareDocRefMove: typeof prepareDocRefMove;
  fetchDocInfo: typeof fetchDocInfo;
}

interface WithProps {
  actionBarItems: Array<ButtonProps>;
}

export interface EnhancedProps
  extends Props,
    WithDocumentTreeProps,
    WithHandlers,
    ConnectState,
    ConnectDispatch,
    SelectableItemListingProps<DocRefType>,
    WithProps {}

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
    (
      { folderExplorer: { documentTree }, selectableItemListings },
      { folderUuid }
    ) => ({
      folder: findItem(documentTree, folderUuid)!,
      selectableItemListing:
        selectableItemListings[LISTING_ID] || defaultSelectableItemListing
    }),
    {
      prepareDocRefCreation,
      prepareDocRefDelete,
      prepareDocRefCopy,
      prepareDocRefRename,
      prepareDocRefMove,
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
  ),
  withProps(
    ({
      folder,
      prepareDocRefCreation,
      prepareDocRefDelete,
      prepareDocRefCopy,
      prepareDocRefRename,
      prepareDocRefMove,
      fetchDocInfo,
      selectableItemListing: { selectedItems, items }
    }) => {
      const actionBarItems: Array<ButtonProps> = [
        {
          icon: "file",
          onClick: () => prepareDocRefCreation(LISTING_ID, folder.node),
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
            onClick: () =>
              prepareDocRefRename(LISTING_ID, singleSelectedDocRef),
            title: "Rename this document"
          });
        }
        actionBarItems.push({
          icon: "copy",
          text: "Copy",
          onClick: d => prepareDocRefCopy(LISTING_ID, selectedDocRefUuids),
          title: "Copy selected documents"
        });
        actionBarItems.push({
          icon: "arrows-alt",
          text: "Move",
          onClick: () => prepareDocRefMove(LISTING_ID, selectedDocRefUuids),
          title: "Move selected documents"
        });
        actionBarItems.push({
          icon: "trash",
          text: "Delete",
          onClick: () => prepareDocRefDelete(LISTING_ID, selectedDocRefUuids),
          title: "Delete selected documents"
        });
      }

      return { actionBarItems };
    }
  )
);

const FolderExplorer = ({
  folder: { node },
  folderUuid,
  actionBarItems,
  onKeyDownWithShortcuts,
  openDocRef
}: EnhancedProps) => (
  <DocRefEditor
    docRef={{
      type: "Folder",
      uuid: folderUuid
    }}
    actionBarItems={actionBarItems}
  >
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
    <MoveDocRefDialog listingId={LISTING_ID} />
    <RenameDocRefDialog listingId={LISTING_ID} />
    <DeleteDocRefDialog listingId={LISTING_ID} />
    <CopyDocRefDialog listingId={LISTING_ID} />
    <NewDocRefDialog listingId={LISTING_ID} />
  </DocRefEditor>
);

export default enhance(FolderExplorer);
