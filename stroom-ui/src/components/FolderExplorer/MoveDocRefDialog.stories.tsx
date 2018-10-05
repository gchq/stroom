import * as React from "react";
import { connect } from "react-redux";
import { storiesOf } from "@storybook/react";

import { DocRefType } from "../../types";
import MoveDocRefDialog from "./MoveDocRefDialog";
import { fromSetupSampleData } from "./test";
import { actionCreators } from "./redux";
import StroomDecorator from "../../lib/storybook/StroomDecorator";

import "../../styles/main.css";

const { prepareDocRefMove } = actionCreators;

const testFolder2 = fromSetupSampleData.children![1];

const LISTING_ID = "test";

interface Props {
  testUuids: Array<string>;
  testDestination: string;
}
interface ConnectState {}
interface ConnectDispatch {
  prepareDocRefMove: typeof prepareDocRefMove;
}
interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

// Move
const TestMoveDialog = connect(
  undefined,
  { prepareDocRefMove }
)(({ prepareDocRefMove, testUuids, testDestination }: EnhancedProps) => (
  <div>
    <button
      onClick={() => prepareDocRefMove(LISTING_ID, testUuids, testDestination)}
    >
      Show
    </button>
    <MoveDocRefDialog listingId={LISTING_ID} />
  </div>
));

storiesOf("Move Doc Ref Dialog", module)
  .addDecorator(StroomDecorator)
  .add("simple", () => (
    <TestMoveDialog
      testUuids={testFolder2.children!.map((d: DocRefType) => d.uuid)}
      testDestination={testFolder2.uuid}
    />
  ));
