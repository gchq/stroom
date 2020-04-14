import { DocRefType } from "components/DocumentEditors/useDocumentApi/types/base";
import { PermissionInheritance } from "../PermissionInheritancePicker/types";

export interface Props {
  uuids: string[];
  initialDestination?: DocRefType;
  isOpen: boolean;
  onConfirm: (
    uuids: string[],
    destination: DocRefType,
    permissionInheritance: PermissionInheritance,
  ) => void;
  onCloseDialog: () => void;
}

export type ShowDialog = (uuids: string[], destination?: DocRefType) => void;

export interface UseDialog {
  showDialog: ShowDialog;
  componentProps: Props;
}
