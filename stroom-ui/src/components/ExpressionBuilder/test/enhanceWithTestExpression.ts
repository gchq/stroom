import { connect } from "react-redux";
import { compose, lifecycle } from "recompose";

import { actionCreators } from "../redux";
import { ExpressionOperatorType } from "../../../types";
import { GlobalStoreState } from "../../../startup/reducers";

const { expressionChanged } = actionCreators;

interface Props {
  expressionId: string;
  testExpression: ExpressionOperatorType;
}

interface ConnectState {}
interface ConnectDispatch {
  expressionChanged: typeof expressionChanged;
}

interface EnhancedProps extends Props {}

export default compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    undefined,
    { expressionChanged }
  ),
  lifecycle<Props & ConnectState & ConnectDispatch, {}>({
    componentDidMount() {
      const { expressionChanged, expressionId, testExpression } = this.props;
      expressionChanged(expressionId, testExpression);
    }
  })
);
