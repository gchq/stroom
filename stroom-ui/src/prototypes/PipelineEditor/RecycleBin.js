import React from 'react';
import PropTypes from 'prop-types';

import { compose } from 'recompose';
import { connect } from 'react-redux';
import { DragSource, DropTarget } from 'react-dnd';
import { Icon } from 'semantic-ui-react';

import { ItemTypes } from './dragDropTypes';
import { actionCreators } from './redux';

const { pipelineElementDeleted } = actionCreators;

const dropTarget = {
  canDrop(props, monitor) {
    return true;
  },
  drop(props, monitor) {
    const { elementId } = monitor.getItem();
    const { pipelineId, pipelineElementDeleted } = props;
    pipelineElementDeleted(pipelineId, elementId);
  },
};

function dropCollect(connect, monitor) {
  return {
    connectDropTarget: connect.dropTarget(),
    isOver: monitor.isOver(),
  };
}

const RecycleBin = ({ connectDropTarget, isOver }) => {
  let color = 'black';
  if (isOver) {
    color = 'red';
  }

  return connectDropTarget(<div>
    <Icon color={color} size="huge" name="trash" />
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
};

export default compose(
  connect(state => ({}), { pipelineElementDeleted }),
  DropTarget([ItemTypes.ELEMENT], dropTarget, dropCollect),
)(RecycleBin);
