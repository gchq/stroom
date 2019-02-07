import * as React from "react";
import { connect } from "react-redux";
import { storiesOf } from "@storybook/react";

import DeleteDocRefDialog from "./DeleteDocRefDialog";
import StroomDecorator from "../../lib/storybook/StroomDecorator";
import { fromSetupSampleData } from "./test";
import { DocRefType } from "../../types";

import "../../styles/main.css";

const testFolder2 = fromSetupSampleData.children![1];

const LISTING_ID = "test";

interface Props {
  testUuids: Array<string>;
}

// Delete
const TestDeleteDialog = ({testUuids}: Props) => (
  <div>
    <button onClick={() => prepareDocRefDelete(LISTING_ID, testUuids)}>
      Show
    </button>
    <DeleteDocRefDialog uuids={} />
  </div>
);

storiesOf("Explorer/Delete Doc Ref Dialog", module)
  .addDecorator(StroomDecorator)
  .add("simple", () => (
    <TestDeleteDialog
      testUuids={testFolder2.children!.map((d: DocRefType) => d.uuid)}
    />
  ));
