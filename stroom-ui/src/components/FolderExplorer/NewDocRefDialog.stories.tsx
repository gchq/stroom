import * as React from "react";
import { storiesOf } from "@storybook/react";
import { connect } from "react-redux";

import NewDocRefDialog from "./NewDocRefDialog";
import { fromSetupSampleData } from "./test";
import { actionCreators } from "./redux";
import { DocRefType } from "../../types";
import { GlobalStoreState } from "../../startup/reducers";
import StroomDecorator from "../../lib/storybook/StroomDecorator";

const { prepareDocRefCreation } = actionCreators;

const testFolder2 = fromSetupSampleData.children![1];

const LISTING_ID = "test";

interface Props {
  testDestination: DocRefType;
}
interface ConnectState {}
interface ConnectDispatch {
  prepareDocRefCreation: typeof prepareDocRefCreation;
}
interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

// New Doc
const TestNewDocRefDialog = connect<
  ConnectState,
  ConnectDispatch,
  Props,
  GlobalStoreState
>(
  undefined,
  { prepareDocRefCreation }
)(({ prepareDocRefCreation, testDestination }: EnhancedProps) => (
  <div>
    <button onClick={() => prepareDocRefCreation(LISTING_ID, testDestination)}>
      Show
    </button>
    <NewDocRefDialog listingId={LISTING_ID} />
  </div>
));

storiesOf("Create Doc Ref Dialog", module)
  .addDecorator(StroomDecorator)
  .add("simple", () => <TestNewDocRefDialog testDestination={testFolder2} />);
