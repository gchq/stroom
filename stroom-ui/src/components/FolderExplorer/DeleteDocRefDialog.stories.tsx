import * as React from "react";
import { useState } from "react";
import { storiesOf } from "@storybook/react";

import DeleteDocRefDialog from "./DeleteDocRefDialog";
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
  const [uuids, setUuids] = useState<Array<string>>([]);

  return (
    <div>
      <button onClick={() => setUuids(testUuids)}>Show</button>
      <DeleteDocRefDialog uuids={uuids} onCloseDialog={() => setUuids([])} />
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
