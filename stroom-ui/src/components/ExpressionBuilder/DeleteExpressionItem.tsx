import { compose, withHandlers, withProps } from "recompose";
import { connect } from "react-redux";

import ThemedConfirm, { Props as ConfirmProps } from "../ThemedConfirm";
import { actionCreators, StoreStateById as ExpressionState } from "./redux";
import { GlobalStoreState } from "../../startup/reducers";

const {
  expressionItemDeleteCancelled,
  expressionItemDeleteConfirmed
} = actionCreators;

export interface Props {
  expressionId: string;
}
interface ConnectState {
  expressionState: ExpressionState;
}
interface ConnectDispatch {
  expressionItemDeleteCancelled: typeof expressionItemDeleteCancelled;
  expressionItemDeleteConfirmed: typeof expressionItemDeleteConfirmed;
}
interface WithHandlers {
  onCancel: () => void;
  onConfirm: () => void;
}
interface WithProps {
  isOpen: boolean;
  question: string;
}
export interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    WithHandlers,
    WithProps {}

const enhance = compose<ConfirmProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ expressionBuilder }, { expressionId }) => ({
      expressionState: expressionBuilder[expressionId]
    }),
    {
      expressionItemDeleteCancelled,
      expressionItemDeleteConfirmed
    }
  ),
  withHandlers<Props & ConnectState & ConnectDispatch, WithHandlers>({
    onCancel: ({ expressionId, expressionItemDeleteCancelled }) => () =>
      expressionItemDeleteCancelled(expressionId),
    onConfirm: ({ expressionId, expressionItemDeleteConfirmed }) => () =>
      expressionItemDeleteConfirmed(expressionId)
  }),
  withProps<WithProps, Props & ConnectState>(
    ({ expressionState: { pendingDeletionOperatorId } }) => ({
      isOpen: !!pendingDeletionOperatorId,
      question: `Delete ${pendingDeletionOperatorId} from expression?`
    })
  )
);

export default enhance(ThemedConfirm);
