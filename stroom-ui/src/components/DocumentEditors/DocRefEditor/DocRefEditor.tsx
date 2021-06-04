import * as React from "react";

import {
  UseDocRefEditorProps,
  DocRefEditorProps,
  UseDocRefEditorPropsIn,
} from "./types";

import AppSearchBar from "../../AppSearchBar";
import DocRefIconHeader from "../../DocRefIconHeader";
import DocRefBreadcrumb from "../../DocRefBreadcrumb";
import Button from "../../Button";
import { useAppNavigation } from "lib/useAppNavigation";
import { DocumentApi } from "components/DocumentEditors/useDocumentApi/types/documentApi";
import { ButtonProps } from "components/Button/types";
import useDocument from "../api/explorer/useDocument";
import { PropsWithChildren } from "react";

function DocRefEditor<T>(
  props: PropsWithChildren<DocRefEditorProps<T>>,
): React.ReactElement {
  const {
    nav: { goToAuthorisationsForDocument, goToEditDocRef },
  } = useAppNavigation();
  const { node: docRef } = useDocument(props.docRefUuid);

  const openDocRefPermissions = React.useCallback(
    () => goToAuthorisationsForDocument(props.docRefUuid),
    [goToAuthorisationsForDocument, props.docRefUuid],
  );

  let actionBarItems: ButtonProps[] = [];
  if (props.onClickSave) {
    actionBarItems.push({
      icon: "save",
      children: "Save",
      disabled: !props.isDirty,
      title: props.isDirty ? "Save" : "Saved",
      onClick: props.onClickSave,
    });
  }

  actionBarItems = actionBarItems.concat(props.additionalActionBarItems || []);

  actionBarItems.push({
    icon: "key",
    children: "Permissions",
    title: "Permissions",
    onClick: openDocRefPermissions,
  });

  return (
    <div className="page">
      <div className="page__header">
        <DocRefIconHeader
          docRefType={docRef.type}
          text={docRef.name || "no name"}
        />
        <div className="page__buttons Button__container">
          {actionBarItems.map((actionBarItem, i) => (
            <Button key={i} {...actionBarItem} />
          ))}
        </div>
        <div className="page__breadcrumb">
          <DocRefBreadcrumb
            docRefUuid={docRef.uuid}
            openDocRef={goToEditDocRef}
          />
        </div>
      </div>
      {props.showAppSearchBar ? (
        <div className="page__search">
          <AppSearchBar
            className="DocRefEditor__search"
            onChange={goToEditDocRef}
          />
        </div>
      ) : undefined}
      <div className="page__body">{props.children}</div>
    </div>
  );
}

export function useDocRefEditor<T>({
  docRefUuid,
  documentApi,
}: UseDocRefEditorPropsIn<T>): UseDocRefEditorProps<T> {
  const [isDirty, setIsDirty] = React.useState<boolean>(false);
  const [docRefContents, setDocRefContents] =
    React.useState<T | undefined>(undefined);

  const saveDocument: DocumentApi<T>["saveDocument"] | undefined = documentApi
    ? documentApi.saveDocument
    : undefined;
  const fetchDocument: DocumentApi<T>["fetchDocument"] | undefined = documentApi
    ? documentApi.fetchDocument
    : undefined;

  React.useEffect(() => {
    if (fetchDocument) {
      fetchDocument(docRefUuid).then((d) => {
        setDocRefContents(d);
        setIsDirty(false);
      });
    }
  }, [fetchDocument, setDocRefContents, setIsDirty, docRefUuid]);

  const onClickSave = React.useCallback(() => {
    if (!!docRefContents && !!saveDocument) {
      saveDocument(docRefContents as unknown as T).then(() => {
        setIsDirty(false);
      });
    }
  }, [saveDocument, docRefContents, setIsDirty]);

  return {
    onDocumentChange: React.useCallback(
      (updates: Partial<T>) => {
        if (docRefContents) {
          setDocRefContents({ ...docRefContents, ...updates });
          setIsDirty(true);
        } else {
          console.error("No existing doc ref contents to merge in");
        }
      },
      [docRefContents, setIsDirty, setDocRefContents],
    ),
    editorProps: {
      isDirty,
      docRefContents: docRefContents
        ? (docRefContents as unknown as T)
        : undefined,
      onClickSave: documentApi ? onClickSave : undefined,
      docRefUuid,
    },
  };
}

export default DocRefEditor;
