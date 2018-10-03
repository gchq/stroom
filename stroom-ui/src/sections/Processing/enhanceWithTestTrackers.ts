import { compose, lifecycle } from "recompose";
import { connect } from "react-redux";

import { actionCreators } from "./redux";
import { GlobalStoreState } from "../../startup/reducers";
import { StreamTaskType } from "../../types";

const { updateTrackerSelection, updateTrackers } = actionCreators;

interface Props {
  testTrackers: Array<StreamTaskType>;
  testTrackerSelection: number;
}
interface ConnectState {}
interface ConnectDispatch {
  updateTrackers: typeof updateTrackers;
  updateTrackerSelection: typeof updateTrackerSelection;
}
interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

export default compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    undefined,
    {
      updateTrackers,
      updateTrackerSelection
    }
  ),
  lifecycle<EnhancedProps, {}>({
    componentDidMount() {
      const {
        updateTrackers,
        updateTrackerSelection,
        testTrackers,
        testTrackerSelection
      } = this.props;

      updateTrackerSelection(testTrackerSelection);
      updateTrackers(testTrackers, testTrackers.length);
    }
  })
);
