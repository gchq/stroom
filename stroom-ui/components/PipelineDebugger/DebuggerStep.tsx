import * as React from "react";
import { compose } from "recompose";
import { connect } from "react-redux";

const enhance = compose(
  connect(
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
