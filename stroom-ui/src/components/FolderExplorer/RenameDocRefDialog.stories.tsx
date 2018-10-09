import * as React from "react";
import { storiesOf } from "@storybook/react";
import { DocRefType } from "../../types";
import { GlobalStoreState } from "../../startup/reducers";

import { connect } from "react-redux";

import RenameDocRefDialog from "./RenameDocRefDialog";
import { fromSetupSampleData } from "./test";
import { actionCreators } from "./redux";
import StroomDecorator from "../../lib/storybook/StroomDecorator";

import "../../styles/main.css";

const { prepareDocRefRename } = actionCreators;

const testDocRef = fromSetupSampleData.children![0].children![0].children![0];

const LISTING_ID = "test";

interface Props {
  testDocRef: DocRefType;
}
interface ConnectState {}
interface ConnectDispatch {
  prepareDocRefRename: typeof prepareDocRefRename;
}
interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

// Rename
const TestRenameDialog = connect<
  ConnectState,
  ConnectDispatch,
  Props,
  GlobalStoreState
>(
  ({}) => ({}),
  { prepareDocRefRename }
)(({ prepareDocRefRename, testDocRef }: EnhancedProps) => (
  <div>
    <button onClick={() => prepareDocRefRename(LISTING_ID, testDocRef)}>
      Show
    </button>
    <RenameDocRefDialog listingId={LISTING_ID} />
  </div>
));

storiesOf("Rename Doc Ref Dialog", module)
  .addDecorator(StroomDecorator)
  .add("simple", () => <TestRenameDialog testDocRef={testDocRef} />);
