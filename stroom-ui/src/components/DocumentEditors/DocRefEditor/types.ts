import { DocumentApi } from "components/DocumentEditors/useDocumentApi/types/documentApi";
import { ButtonProps } from "components/Button/types";

export interface DocRefEditorProps<T> {
  docRefUuid: string;
  additionalActionBarItems?: ButtonProps[];
  onClickSave?: () => void;
  isDirty: boolean;
  docRefContents?: T;
  children?: React.ReactNode;
  showAppSearchBar?: boolean;
}

export interface UseDocRefEditorPropsIn<T extends object> {
  docRefUuid: string;
  documentApi?: DocumentApi<T>;
}

export interface UseDocRefEditorProps<T extends object> {
  editorProps: DocRefEditorProps<T>;
  onDocumentChange: (updates: Partial<T>) => void;
}

export interface SwitchedDocRefEditorProps {
  docRefUuid: string;
}
