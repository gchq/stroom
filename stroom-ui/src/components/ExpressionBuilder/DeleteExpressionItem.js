import PropTypes from 'prop-types';
import { compose, withHandlers, withProps } from 'recompose';
import { connect } from 'react-redux';

import { ThemedConfirm } from 'components/ThemedModal';
import { actionCreators } from './redux';

const { expressionItemDeleteCancelled, expressionItemDeleteConfirmed } = actionCreators;

const enhance = compose(
  connect(
    ({ expressionBuilder }, { expressionId }) => ({
      expressionState: expressionBuilder[expressionId],
    }),
    {
      expressionItemDeleteCancelled,
      expressionItemDeleteConfirmed,
    },
  ),
  withHandlers({
    onCancel: ({ expressionId, expressionItemDeleteCancelled }) => () =>
      expressionItemDeleteCancelled(expressionId),
    onConfirm: ({ expressionId, expressionItemDeleteConfirmed }) => () =>
      expressionItemDeleteConfirmed(expressionId),
  }),
  withProps(({ pendingDeletionOperatorId }) => ({
    isOpen: !!pendingDeletionOperatorId,
    question: `Delete ${pendingDeletionOperatorId} from expression?`,
  })),
);

const DeleteExpressionItem = enhance(ThemedConfirm);

DeleteExpressionItem.propTypes = {
  expressionId: PropTypes.string.isRequired,
};

export default DeleteExpressionItem;
