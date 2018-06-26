import React from 'react';
import PropTypes from 'prop-types';

import { compose, withState, withProps } from 'recompose';
import { connect } from 'react-redux';
import { DropTarget } from 'react-dnd';
import { Icon, Confirm, Button } from 'semantic-ui-react';

import { ItemTypes } from './dragDropTypes';
import { actionCreators } from './redux';

const { pipelineElementDeleted } = actionCreators;

const withPendingDeletion = withState(
  'pendingElementToDelete',
  'setPendingElementToDelete',
  undefined,
);

const dropTarget = {
  canDrop(props, monitor) {
    return true;
  },
  drop(props, monitor) {
    const { elementId } = monitor.getItem();
    const { setPendingElementToDelete } = props;
    setPendingElementToDelete(elementId);
  },
};

const dropCollect = (connect, monitor) => ({
  connectDropTarget: connect.dropTarget(),
  isOver: monitor.isOver(),
  dndIsHappening: monitor.getItem() !== null,
});

const enhance = compose(
  connect(state => ({}), { pipelineElementDeleted }),
  withPendingDeletion,
  DropTarget([ItemTypes.ELEMENT], dropTarget, dropCollect),
  withProps(({
    pipelineId,
    isOver,
    pendingElementToDelete,
    setPendingElementToDelete,
    pipelineElementDeleted,
  }) => ({
    onCancelDelete: () => setPendingElementToDelete(undefined),
    onConfirmDelete: () => {
      pipelineElementDeleted(pipelineId, pendingElementToDelete);
      setPendingElementToDelete(undefined);
    },
  })),
);

const Bin = enhance(({
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
      size="huge"
      circular
      disabled={!dndIsHappening}
      color={isOver ? 'black' : 'red'}
      icon="trash"
    />
                    </div>));

Bin.propTypes = {
  // From container
  pipelineId: PropTypes.string.isRequired,
};

export default Bin;
