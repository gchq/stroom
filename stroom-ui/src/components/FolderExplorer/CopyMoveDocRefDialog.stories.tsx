import * as React from "react";
import { storiesOf } from "@storybook/react";

import StroomDecorator from "../../lib/storybook/StroomDecorator";
import { fromSetupSampleData } from "./test";
import CopyDocRefDialog, {
  useDialog as useCopyMoveDocRefDialog
} from "./CopyMoveDocRefDialog";

import "../../styles/main.css";
import { PermissionInheritance } from "src/types";

const testFolder2 = fromSetupSampleData.children![1];

interface Props {
  testUuids: Array<string>;
  testDestination: string;
}

// Copy
const TestCopyDialog = ({ testUuids, testDestination }: Props) => {
  const { showDialog, componentProps } = useCopyMoveDocRefDialog(
    (
      uuids: Array<string>,
      destinationUuid: string,
      permissionInheritance: PermissionInheritance
    ) => {
      console.log("Yaaas", { uuids, destinationUuid, permissionInheritance });
    }
  );

  return (
    <div>
      <h1>Copy Doc Ref Test</h1>
      <button onClick={() => showDialog(testUuids, testDestination)}>
        Show
      </button>
      <CopyDocRefDialog {...componentProps} />
    </div>
  );
};

storiesOf("Explorer/Copy Doc Ref Dialog", module)
  .addDecorator(StroomDecorator)
  .add("simple", () => (
    <TestCopyDialog
      testUuids={testFolder2.children!.map(d => d.uuid)}
      testDestination={testFolder2.uuid}
    />
  ));
