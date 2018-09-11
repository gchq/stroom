import React from 'react';
import PropTypes from 'prop-types';
import { compose, withHandlers } from 'recompose';
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
    onCancelDelete: ({ expressionId, expressionItemDeleteCancelled }) => () =>
      expressionItemDeleteCancelled(expressionId),
    onConfirmDelete: ({ expressionId, expressionItemDeleteConfirmed }) => () =>
      expressionItemDeleteConfirmed(expressionId),
  }),
);

const DeleteExpressionItem = ({
  expressionState: { pendingDeletionOperatorId },
  onConfirmDelete,
  onCancelDelete,
}) => (
    <ThemedConfirm
      isOpen={!!pendingDeletionOperatorId}
      question={`Delete ${pendingDeletionOperatorId} from expression?`}
      onCancel={onCancelDelete}
      onConfirm={onConfirmDelete}
    />
  );

DeleteExpressionItem.propTypes = {
  expressionId: PropTypes.string.isRequired,
};

export default enhance(DeleteExpressionItem);
