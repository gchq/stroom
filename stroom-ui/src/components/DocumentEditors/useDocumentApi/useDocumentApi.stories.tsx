import * as React from "react";

import { storiesOf } from "@storybook/react";
import DocRefTypePicker from "components/DocRefTypePicker";
import useDocumentApi from "./useDocumentApi";
import { ResourcesByDocType } from "./types/resourceUrls";
import { useDocumentTree } from "components/DocumentEditors/api/explorer";
import { iterateNodes } from "lib/treeUtils/treeUtils";
import JsonDebug from "testing/JsonDebug";

const TestHarness: React.FunctionComponent = () => {
  const { documentTree } = useDocumentTree();
  const [docRefContents, setDocRefContents] = React.useState<object>({});
  const [docRefType, setDocRefType] = React.useState<keyof ResourcesByDocType>(
    "Dictionary",
  );
  const setDocRefTypeSafe = React.useCallback(
    (d) => setDocRefType(d as keyof ResourcesByDocType),
    [setDocRefType],
  );

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

  const { fetchDocument } = useDocumentApi(
    docRefType as keyof ResourcesByDocType,
  );

  React.useEffect(() => {
    if (!!fetchDocument) {
      fetchDocument(docRefUuid).then((d) => {
        setDocRefContents(d);
      });
    }
  }, [fetchDocument, setDocRefContents, docRefUuid]);

  return (
    <div>
      <DocRefTypePicker
        value={docRefType}
        onChange={setDocRefTypeSafe}
        invalidTypes={["Folder"]}
      />
      <JsonDebug value={{ docRefType, docRefUuid, docRefContents }} />
    </div>
  );
};

storiesOf("Document Editors/useDocumentApi", module).add("test", () => (
  <TestHarness />
));
