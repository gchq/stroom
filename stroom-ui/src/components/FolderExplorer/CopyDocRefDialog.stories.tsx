import * as React from "react";
import { connect } from "react-redux";
import { storiesOf } from "@storybook/react";

import StroomDecorator from "../../lib/storybook/StroomDecorator";
import { fromSetupSampleData } from "./test";
import { actionCreators } from "./redux";
import { GlobalStoreState } from "../../startup/reducers";
import CopyDocRefDialog from "./CopyDocRefDialog";

import "../../styles/main.css";

const { prepareDocRefCopy } = actionCreators;

const testFolder2 = fromSetupSampleData.children![1];

const LISTING_ID = "test";

interface Props {
  testUuids: Array<string>;
  testDestination: string;
}
interface ConnectState {}
interface ConnectDispatch {
  prepareDocRefCopy: typeof prepareDocRefCopy;
}
interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}
// Copy
const TestCopyDialog = connect<
  ConnectState,
  ConnectDispatch,
  Props,
  GlobalStoreState
>(
  undefined,
  { prepareDocRefCopy }
)(({ prepareDocRefCopy, testUuids, testDestination }: EnhancedProps) => (
  <div>
    <button
      onClick={() => prepareDocRefCopy(LISTING_ID, testUuids, testDestination)}
    >
      Show
    </button>
    <CopyDocRefDialog listingId={LISTING_ID} />
  </div>
));

storiesOf("Copy Doc Ref Dialog", module)
  .addDecorator(StroomDecorator)
  .add("simple", () => (
    <TestCopyDialog
      testUuids={testFolder2.children!.map(d => d.uuid)}
      testDestination={testFolder2.uuid}
    />
  ));
