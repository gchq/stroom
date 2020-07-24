import * as React from "react";
import { storiesOf } from "@storybook/react";

import RenameDocRefDialog, { useDialog } from "./RenameDocRefDialog";

import JsonDebug from "testing/JsonDebug";
import { DocRefType } from "components/DocumentEditors/useDocumentApi/types/base";
import fullTestData from "testing/data";

const testDocRef = fullTestData.documentTree.children![0].children![0]
  .children![0];

interface Props {
  testDocRef: DocRefType;
}

// Rename
const TestHarness: React.FunctionComponent<Props> = ({ testDocRef }) => {
  const [lastConfirmed, setLastConfirmed] = React.useState<object>({});
  const { showDialog, componentProps } = useDialog((docRef, newName) =>
    setLastConfirmed({ docRef, newName }),
  );

  return (
    <div>
      <h1>Rename Document Test</h1>
      <button onClick={() => showDialog(testDocRef)}>Show</button>
      <RenameDocRefDialog {...componentProps} />
      <JsonDebug value={lastConfirmed} />
    </div>
  );
};

storiesOf("Document Editors/Folder/Rename Doc Ref", module).add(
  "Dialog",
  () => <TestHarness testDocRef={testDocRef} />,
);
