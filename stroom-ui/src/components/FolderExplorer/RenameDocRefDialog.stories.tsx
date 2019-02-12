import * as React from "react";
import { storiesOf } from "@storybook/react";
import { DocRefType } from "../../types";

import RenameDocRefDialog, { useDialog } from "./RenameDocRefDialog";
import { fromSetupSampleData } from "./test";
import StroomDecorator from "../../lib/storybook/StroomDecorator";

import "../../styles/main.css";

const testDocRef = fromSetupSampleData.children![0].children![0].children![0];

interface Props {
  testDocRef: DocRefType;
}

// Rename
const TestRenameDialog = ({ testDocRef }: Props) => {
  const { showDialog, componentProps } = useDialog();

  return (
    <div>
      <h1>Rename Document Test</h1>
      <button onClick={() => showDialog(testDocRef)}>Show</button>
      <RenameDocRefDialog {...componentProps} />
    </div>
  );
};

storiesOf("Explorer/Rename Doc Ref Dialog", module)
  .addDecorator(StroomDecorator)
  .add("simple", () => <TestRenameDialog testDocRef={testDocRef} />);
