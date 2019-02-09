import * as React from "react";
import { storiesOf } from "@storybook/react";

import CreateDocRefDialog, {
  useCreateDocRefDialog
} from "./CreateDocRefDialog";
import { fromSetupSampleData } from "./test";
import { DocRefType } from "../../types";
import StroomDecorator from "../../lib/storybook/StroomDecorator";

import "../../styles/main.css";

const testFolder2 = fromSetupSampleData.children![1];

interface Props {
  testDestination: DocRefType;
}

// New Doc
const TestNewDocRefDialog = ({ testDestination }: Props) => {
  const { showCreateDialog, componentProps } = useCreateDocRefDialog();

  return (
    <div>
      <h1>Create Doc Ref Test</h1>
      <button onClick={() => showCreateDialog(testDestination)}>Show</button>
      <CreateDocRefDialog {...componentProps} />
    </div>
  );
};

storiesOf("Explorer/Create Doc Ref Dialog", module)
  .addDecorator(StroomDecorator)
  .add("simple", () => <TestNewDocRefDialog testDestination={testFolder2} />);
