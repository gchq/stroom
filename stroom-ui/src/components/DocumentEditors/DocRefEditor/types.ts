import { DocumentApi } from "components/DocumentEditors/useDocumentApi/types/documentApi";
import { ButtonProps } from "components/Button/types";
import React from "react";

export interface DocRefEditorProps<T> {
  docRefUuid: string;
  additionalActionBarItems?: ButtonProps[];
  onClickSave?: () => void;
  isDirty: boolean;
  docRefContents?: T;
  children?: React.ReactNode;
  showAppSearchBar?: boolean;
}

export interface UseDocRefEditorPropsIn<T> {
  docRefUuid: string;
  documentApi?: DocumentApi<T>;
}

export interface UseDocRefEditorProps<T> {
  editorProps: DocRefEditorProps<T>;
  onDocumentChange: (updates: Partial<T>) => void;
}

export interface SwitchedDocRefEditorProps {
  docRefUuid: string;
}
