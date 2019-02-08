import * as React from "react";
import { storiesOf } from "@storybook/react";

import DeleteDocRefDialog, {
  useDeleteDocRefDialog
} from "./DeleteDocRefDialog";
import StroomDecorator from "../../lib/storybook/StroomDecorator";
import { fromSetupSampleData } from "./test";
import { DocRefType } from "../../types";

import "../../styles/main.css";

const testFolder2 = fromSetupSampleData.children![1];

interface Props {
  testUuids: Array<string>;
}

// Delete
const TestDeleteDialog = ({ testUuids }: Props) => {
  const { startToDeleteDocRefs, componentProps } = useDeleteDocRefDialog();

  return (
    <div>
      <button onClick={() => startToDeleteDocRefs(testUuids)}>Show</button>
      <DeleteDocRefDialog {...componentProps} />
    </div>
  );
};

storiesOf("Explorer/Delete Doc Ref Dialog", module)
  .addDecorator(StroomDecorator)
  .add("simple", () => (
    <TestDeleteDialog
      testUuids={testFolder2.children!.map((d: DocRefType) => d.uuid)}
    />
  ));
