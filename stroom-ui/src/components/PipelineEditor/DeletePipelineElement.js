import React from 'react';
import PropTypes from 'prop-types';
import { compose, withHandlers } from 'recompose';
import { connect } from 'react-redux';

import { ThemedConfirm } from 'components/ThemedModal';
import { actionCreators } from './redux';

const { pipelineElementDeleteCancelled, pipelineElementDeleteConfirmed } = actionCreators;

const enhance = compose(
  connect(
    ({ pipelineEditor: { elements, pipelineStates } }, { pipelineId }) => ({
      pipelineState: pipelineStates[pipelineId],
      elements,
    }),
    {
      pipelineElementDeleteCancelled,
      pipelineElementDeleteConfirmed,
    },
  ),
  withHandlers({
    onCancelDelete: ({ pipelineElementDeleteCancelled, pipelineId }) => () =>
      pipelineElementDeleteCancelled(pipelineId),
    onConfirmDelete: ({ pipelineElementDeleteConfirmed, pipelineId }) => () =>
      pipelineElementDeleteConfirmed(pipelineId),
  }),
);

const DeletePipelineElement = ({
  pipelineState: { pendingElementIdToDelete },
  onConfirmDelete,
  onCancelDelete,
}) => (
  <ThemedConfirm
    open={!!pendingElementIdToDelete}
    question={`Delete ${pendingElementIdToDelete} from pipeline?`}
    onCancel={onCancelDelete}
    onConfirm={onConfirmDelete}
  />
);

DeletePipelineElement.propTypes = {
  pipelineId: PropTypes.string.isRequired,
};

export default enhance(DeletePipelineElement);
