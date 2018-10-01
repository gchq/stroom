import * as React from "react";
import { compose } from "recompose";
import { connect } from "react-redux";
import { GlobalStoreState } from "../../startup/reducers";

export interface Props {
  debuggerId: string;
}
interface ConnectState {}
interface ConnectDispatch {}
export interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    (
      {
        /* state desctructuring */
      }
    ) => ({
      /*mapStateToProps */
    }),
    {
      /* mapDispatchToProps */
    }
  )
);

const DebuggerStep = ({}) => <div>TODO: debugger step information</div>;

export default enhance(DebuggerStep);
