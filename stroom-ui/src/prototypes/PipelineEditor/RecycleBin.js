import React from 'react';
import PropTypes from 'prop-types';

import { compose, withState } from 'recompose';
import { connect } from 'react-redux';
import { DropTarget } from 'react-dnd';
import { Icon, Confirm } from 'semantic-ui-react';

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

function dropCollect(connect, monitor) {
  return {
    connectDropTarget: connect.dropTarget(),
    isOver: monitor.isOver(),
  };
}

const RecycleBin = ({
  connectDropTarget,
  isOver,
  pendingElementToDelete,
  setPendingElementToDelete,
  pipelineElementDeleted,
  pipelineId,
}) => {
  let className = 'recycle-bin__icon';
  if (isOver) {
    className = 'recycle-bin__icon__hover';
  }

  const onCancelDelete = () => setPendingElementToDelete(undefined);

  const onConfirmDelete = () => {
    pipelineElementDeleted(pipelineId, pendingElementToDelete);
    setPendingElementToDelete(undefined);
  };

  let confirmDeleteContent;
  if (pendingElementToDelete) {
    confirmDeleteContent = `Delete ${pendingElementToDelete} from pipeline?`;
  }

  return connectDropTarget(<div>
    <Confirm
      open={!!pendingElementToDelete}
      content={confirmDeleteContent}
      onCancel={onCancelDelete}
      onConfirm={onConfirmDelete}
    />
    <Icon className={className} size="huge" name="trash" />
  </div>);
};

RecycleBin.propTypes = {
  // From container
  pipelineId: PropTypes.string.isRequired,

  // Drop Target
  connectDropTarget: PropTypes.func.isRequired,
  isOver: PropTypes.bool.isRequired,

  // Redux action
  pipelineElementDeleted: PropTypes.func.isRequired,

  // withPendingDeletion
  pendingElementToDelete: PropTypes.string,
  setPendingElementToDelete: PropTypes.func.isRequired,
};

export default compose(
  connect(state => ({}), { pipelineElementDeleted }),
  withPendingDeletion,
  DropTarget([ItemTypes.ELEMENT], dropTarget, dropCollect),
)(RecycleBin);
