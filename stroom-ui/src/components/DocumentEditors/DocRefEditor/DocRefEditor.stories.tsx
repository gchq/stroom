import * as React from "react";

import { storiesOf } from "@storybook/react";
import DocRefEditor, { useDocRefEditor } from ".";
import { useDocumentTree } from "components/DocumentEditors/api/explorer";
import { iterateNodes } from "lib/treeUtils/treeUtils";
import DocRefTypePicker from "components/DocRefTypePicker";
import {
  useDocumentApi,
  ResourcesByDocType,
} from "components/DocumentEditors/useDocumentApi";
import JsonDebug from "testing/JsonDebug";

const TestHarness: React.FunctionComponent = () => {
  const { documentTree } = useDocumentTree();
  const [docRefType, setDocRefType] = React.useState<keyof ResourcesByDocType>(
    "Dictionary",
  );
  const setDocRefTypeSafe = React.useCallback(
    (d) => setDocRefType(d as keyof ResourcesByDocType),
    [setDocRefType],
  );
  const documentApi = useDocumentApi(docRefType);

  const docRefUuid = React.useMemo(() => {
    let d;
    iterateNodes(documentTree, (_, node) => {
      if (node.type === docRefType) {
        d = node.uuid;
        return true;
      }
      return false;
    });

    return d || documentTree.uuid;
  }, [docRefType, documentTree]);

  const { editorProps, onDocumentChange } = useDocRefEditor({
    docRefUuid,
    documentApi,
  });
  const { docRefContents } = editorProps;

  return !!docRefContents ? (
    <DocRefEditor {...editorProps}>
      <DocRefTypePicker value={docRefType} onChange={setDocRefTypeSafe} />
      <JsonDebug
        value={{
          documentApi: Object.keys(documentApi),
          docRefContents,
          onDocumentChange: JSON.stringify(onDocumentChange),
        }}
      />
    </DocRefEditor>
  ) : (
    <div>Nowt Available</div>
  );
};

storiesOf("Document Editors", module).add("Document Editors", () => (
  <TestHarness />
));
