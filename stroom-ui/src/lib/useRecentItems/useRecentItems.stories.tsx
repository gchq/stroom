import * as React from "react";

import { storiesOf } from "@storybook/react";
import useRecentItems from ".";
import { useDocumentTree } from "components/DocumentEditors/api/explorer";
import { iterateNodes } from "../treeUtils/treeUtils";
import Button from "components/Button";
import {
  DocRefType,
  copyDocRef,
} from "components/DocumentEditors/useDocumentApi/types/base";

const TestHarness: React.FunctionComponent = () => {
  const { recentItems, addRecentItem } = useRecentItems();
  const { documentTree } = useDocumentTree();
  const documents = React.useMemo(() => {
    const d: DocRefType[] = [];
    iterateNodes(documentTree, (_, node) => d.push(node));
    return d;
  }, [documentTree]);
  const [documentIndex, setDocumentIndex] = React.useState<number>(0);
  const onClickAddNext = React.useCallback(() => {
    addRecentItem(documents[documentIndex]);
    setDocumentIndex((documentIndex + 1) % documents.length);
  }, [documents, documentIndex, addRecentItem, setDocumentIndex]);

  return (
    <div>
      <Button onClick={onClickAddNext}>Add</Button>
      <ul>
        {recentItems.map((recentItem) => (
          <li key={recentItem.uuid}>
            {JSON.stringify(copyDocRef(recentItem))}
          </li>
        ))}
      </ul>
    </div>
  );
};

storiesOf("lib/useRecentItems", module).add("test", () => <TestHarness />);
