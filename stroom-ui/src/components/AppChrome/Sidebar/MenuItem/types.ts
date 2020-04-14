import { IconProp } from "@fortawesome/fontawesome-svg-core";
import { DocRefType } from "components/DocumentEditors/useDocumentApi/types/base";

export type MenuItemOpened = (name: string, isOpen: boolean) => void;
export type MenuItemToggled = (name: string) => void;

export interface MenuItemsOpenState {
  [s: string]: boolean;
}

export interface MenuItemType {
  key: string;
  title?: string;
  onClick: () => void;
  icon: IconProp;
  style: "doc" | "nav";
  skipInContractedMenu?: boolean;
  children?: MenuItemType[];
  docRef?: DocRefType;
  parentDocRef?: DocRefType;
}
