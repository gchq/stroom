import * as React from "react";
import { MenuItemType } from "../../MenuItem/types";
import { SubMenuProps } from "../types";
import { useDocumentTree } from "components/DocumentEditors/api/explorer";
import {
  DocRefConsumer,
  DocRefType,
  DocRefTree,
} from "components/DocumentEditors/useDocumentApi/types/base";

const getDocumentTreeMenuItems = (
  openDocRef: DocRefConsumer,
  parentDocRef: DocRefType | undefined,
  treeNode: DocRefTree,
  skipInContractedMenu = false,
): MenuItemType => ({
  key: treeNode.uuid,
  title: treeNode.name,
  onClick: () => openDocRef(treeNode),
  icon: "folder",
  style: skipInContractedMenu ? "doc" : "nav",
  skipInContractedMenu,
  docRef: treeNode,
  parentDocRef,
  children:
    treeNode.children && treeNode.children.length > 0
      ? treeNode.children
          .filter((t) => t.type === "Folder")
          .map((t) => getDocumentTreeMenuItems(openDocRef, treeNode, t, true))
      : undefined,
});

const useDocumentMenu = ({
  navigateApp: {
    nav: { goToEditDocRef },
  },
}: SubMenuProps): MenuItemType => {
  const { documentTree } = useDocumentTree();

  return React.useMemo(
    () => getDocumentTreeMenuItems(goToEditDocRef, undefined, documentTree),
    [documentTree, goToEditDocRef],
  );
};

export default useDocumentMenu;
