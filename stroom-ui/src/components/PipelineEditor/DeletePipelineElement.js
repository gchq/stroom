import React from 'react';
import PropTypes from 'prop-types';
import { compose, withProps } from 'recompose';
import { connect } from 'react-redux';

import ThemedConfirm from 'components/ThemedModal';
import { actionCreators } from './redux';

const { pipelineElementDeleteCancelled, pipelineElementDeleteConfirmed } = actionCreators;

const enhance = compose(
  connect(
    ({ pipelineEditor: { elements, pipelineStates } }, { pipelineId }) => ({
      pipeline: pipelineStates[pipelineId],
      elements,
    }),
    {
      pipelineElementDeleteCancelled,
      pipelineElementDeleteConfirmed,
    },
  ),
  withProps(({
    pipelineId,
    pipeline: { pendingElementIdToDelete },
    pipelineElementDeleteCancelled,
    pipelineElementDeleteConfirmed,
  }) => ({
    onCancelDelete: () => pipelineElementDeleteCancelled(pipelineId),
    onConfirmDelete: () => {
      pipelineElementDeleteConfirmed(pipelineId, pendingElementIdToDelete);
    },
  })),
);

const DeletePipelineElement = ({ pendingElementIdToDelete, onConfirmDelete, onCancelDelete }) => (
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
