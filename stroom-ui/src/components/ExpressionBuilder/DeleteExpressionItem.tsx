import { compose, withHandlers, withProps } from "recompose";
import { connect } from "react-redux";

import ThemedConfirm from "../ThemedConfirm";
import { actionCreators, State } from "./redux";

const {
  expressionItemDeleteCancelled,
  expressionItemDeleteConfirmed
} = actionCreators;

export interface Props {
  expressionId: string;
}
export interface ConnectState {
  expressionState: 
}
export interface EnhancedProps extends Props{};

// DeleteExpressionItem.propTypes = {
//   expressionId: PropTypes.string.isRequired,
// };

const enhance = compose<EnhancedProps, Props>(
  connect(
    ({ expressionBuilder }, { expressionId }) => ({
      expressionState: expressionBuilder[expressionId]
    }),
    {
      expressionItemDeleteCancelled,
      expressionItemDeleteConfirmed
    }
  ),
  withHandlers({
    onCancel: ({ expressionId, expressionItemDeleteCancelled }) => () =>
      expressionItemDeleteCancelled(expressionId),
    onConfirm: ({ expressionId, expressionItemDeleteConfirmed }) => () =>
      expressionItemDeleteConfirmed(expressionId)
  }),
  withProps(({ pendingDeletionOperatorId }) => ({
    isOpen: !!pendingDeletionOperatorId,
    question: `Delete ${pendingDeletionOperatorId} from expression?`
  }))
);

export default enhance(ThemedConfirm);

