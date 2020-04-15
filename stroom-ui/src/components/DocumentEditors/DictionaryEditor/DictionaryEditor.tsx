import * as React from "react";

import Loader from "../../Loader";
import DocRefEditor, { useDocRefEditor } from "../DocRefEditor";
import { SwitchedDocRefEditorProps } from "../DocRefEditor/types";
import useDocumentApi from "components/DocumentEditors/useDocumentApi";
import ThemedAceEditor from "components/ThemedAceEditor";
import { DictionaryDoc } from "components/DocumentEditors/useDocumentApi/types/dictionaryDoc";

const DictionaryEditor: React.FunctionComponent<SwitchedDocRefEditorProps> = ({
  docRefUuid,
}) => {
  // Get data from and subscribe to the store
  const documentApi = useDocumentApi<"Dictionary", DictionaryDoc>("Dictionary");

  const { onDocumentChange, editorProps } = useDocRefEditor<DictionaryDoc>({
    docRefUuid,
    documentApi,
  });
  const { docRefContents } = editorProps;

  const onDataChange = React.useCallback(
    value => onDocumentChange({ data: value }),
    [onDocumentChange],
  );

  return !!docRefContents ? (
    <DocRefEditor {...editorProps}>
      <ThemedAceEditor
        style={{ width: "100%", height: "100%", minHeight: "25rem" }}
        name={`${docRefUuid}-ace-editor`}
        mode="xml"
        value={docRefContents.data || ""}
        onChange={onDataChange}
      />
    </DocRefEditor>
  ) : (
    <Loader message={`Loading DictionaryDoc ${docRefUuid}`} />
  );
};

export default DictionaryEditor;
