import React from 'react';
import PropTypes from 'prop-types';

import { compose, withState, withProps } from 'recompose';
import { connect } from 'react-redux';
import { DropTarget } from 'react-dnd';
import { Confirm, Button } from 'semantic-ui-react';

import ItemTypes from './dragDropTypes';
import { actionCreators } from './redux';

const {
  pipelineElementDeleteRequested,
  pipelineElementDeleteCancelled,
  pipelineElementDeleted,
} = actionCreators;

const dropTarget = {
  canDrop(props, monitor) {
    return true;
  },
  drop({ pipelineElementDeleteRequested }, monitor) {
    const { pipelineId, elementId } = monitor.getItem();
    pipelineElementDeleteRequested(pipelineId, elementId);
  },
};

const dropCollect = (connect, monitor) => ({
  connectDropTarget: connect.dropTarget(),
  isOver: monitor.isOver(),
  dndIsHappening: monitor.getItem() !== null,
});

const enhance = compose(
  connect(
    ({ pipelineEditor }, { pipelineId }) => ({
      pendingElementToDelete: pipelineEditor.pipelines[pipelineId].pendingElementToDelete,
    }),
    { pipelineElementDeleteRequested, pipelineElementDeleteCancelled, pipelineElementDeleted },
  ),
  DropTarget([ItemTypes.ELEMENT], dropTarget, dropCollect),
  withProps(({
    pipelineId,
    isOver,
    pendingElementToDelete,
    pipelineElementDeleted,
    pipelineElementDeleteCancelled,
  }) => ({
    onCancelDelete: () => pipelineElementDeleteCancelled(pipelineId),
    onConfirmDelete: () => {
      pipelineElementDeleted(pipelineId, pendingElementToDelete);
    },
  })),
);

const Bin = ({
  pipelineId,
  connectDropTarget,
  isOver,
  dndIsHappening,
  onCancelDelete,
  onConfirmDelete,
  pendingElementToDelete,
}) =>
  connectDropTarget(<div>
    <Confirm
      open={!!pendingElementToDelete}
      content={`Delete ${pendingElementToDelete} from pipeline?`}
      onCancel={onCancelDelete}
      onConfirm={onConfirmDelete}
    />
    <Button
      circular
      disabled={!dndIsHappening}
      color={isOver ? 'black' : 'red'}
      size="huge"
      icon="trash"
    />
  </div>);

Bin.propTypes = {
  pipelineId: PropTypes.string.isRequired,
};

export default enhance(Bin);
