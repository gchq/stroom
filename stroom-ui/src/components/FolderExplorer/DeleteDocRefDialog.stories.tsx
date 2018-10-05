import * as React from "react";
import { connect } from "react-redux";
import { storiesOf } from "@storybook/react";

import DeleteDocRefDialog from "./DeleteDocRefDialog";
import StroomDecorator from "../../lib/storybook/StroomDecorator";
import { fromSetupSampleData } from "./test";
import { actionCreators } from "./redux";
import { DocRefType } from "../../types";

const { prepareDocRefDelete } = actionCreators;

const testFolder2 = fromSetupSampleData.children![1];

const LISTING_ID = "test";

interface Props {
  testUuids: Array<string>;
}
interface ConnectState {}
interface ConnectDispatch {
  prepareDocRefDelete: typeof prepareDocRefDelete;
}
interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

// Delete
const TestDeleteDialog = connect(
  ({}) => ({}),
  { prepareDocRefDelete }
)(({ prepareDocRefDelete, testUuids }: EnhancedProps) => (
  <div>
    <button onClick={() => prepareDocRefDelete(LISTING_ID, testUuids)}>
      Show
    </button>
    <DeleteDocRefDialog listingId={LISTING_ID} />
  </div>
));

storiesOf("Delete Doc Ref Dialog", module)
  .addDecorator(StroomDecorator)
  .add("simple", () => (
    <TestDeleteDialog
      testUuids={testFolder2.children!.map((d: DocRefType) => d.uuid)}
    />
  ));
