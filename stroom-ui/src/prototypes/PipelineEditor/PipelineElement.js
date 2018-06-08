/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import React from 'react';
import PropTypes from 'prop-types';

import { compose, withState } from 'recompose';
import { connect } from 'react-redux';

import { DragSource, DropTarget } from 'react-dnd';

import { withElement } from './withElement';
import { withPipeline } from './withPipeline';

import { actionCreators } from './redux';

import ElementContextMenu from './ElementContextMenu';

import { canMovePipelineElement } from './pipelineUtils';

import { ItemTypes } from './dragDropTypes';

const {
  pipelineElementSelected,
  pipelineElementMoved,
} = actionCreators;

const withContextMenu = withState('isContextMenuOpen', 'setContextMenuOpen', false);

const dragSource = {
  canDrag(props) {
    return true;
  },
  beginDrag(props) {
    return {
      elementId: props.elementId,
    };
  },
};

function dragCollect(connect, monitor) {
  return {
    connectDragSource: connect.dragSource(),
    isDragging: monitor.isDragging(),
  };
}

const dropTarget = {
  canDrop(props, monitor) {
    return canMovePipelineElement(
      props.pipeline,
      props.asTree,
      monitor.getItem().elementId,
      props.elementId,
    );
  },
  drop(props, monitor) {
    props.pipelineElementMoved(props.pipelineId, monitor.getItem().elementId, props.elementId);
  },
};

function dropCollect(connect, monitor) {
  return {
    connectDropTarget: connect.dropTarget(),
    isOver: monitor.isOver(),
    canDrop: monitor.canDrop(),
  };
}

const PipelineElement = ({
  connectDragSource,
  isDragging,
  connectDropTarget,
  isOver,
  canDrop,
  pipelineId,
  elementId,
  element,
  elementDefinition,
  pipelineElementSelected,

  isContextMenuOpen,
  setContextMenuOpen,
}) => {
  let className = 'Pipeline-element';
  if (isOver) {
    className += ' Pipeline-element__over';
  }
  if (isDragging) {
    className += ' Pipeline-element__dragging ';
  }
  if (isOver) {
    if (canDrop) {
      className += ' Pipeline-element__over_can_drop';
    } else {
      className += ' Pipeline-element__over_cannot_drop';
    }
  }

  const onSingleClick = () => pipelineElementSelected(pipelineId, elementId);
  const onRightClick = (e) => {
    setContextMenuOpen(true);
    e.preventDefault();
  };

  const dndBox = compose(connectDragSource, connectDropTarget)(<div className={className} onClick={onSingleClick} onContextMenu={onRightClick}>
    <img className="Pipeline-element__icon" alt='X' src={require(`./images/${elementDefinition.icon}`)} />
    {elementId}
  </div>);

  return (
    <span>
      {dndBox}
      <span className="Pipeline-element__context-menu">
        <ElementContextMenu
          pipelineId={pipelineId}
          elementId={elementId}
          isOpen={isContextMenuOpen}
          closeContextMenu={() => setContextMenuOpen(false)}
        />
      </span>
    </span>
  );
};

PipelineElement.propTypes = {
  // Set by container
  pipelineId: PropTypes.string.isRequired,
  elementId: PropTypes.string.isRequired,

  // withPipeline
  pipeline: PropTypes.object.isRequired,
  asTree: PropTypes.object.isRequired,
  pipelineElementSelected: PropTypes.func.isRequired,

  // withElement
  element: PropTypes.object.isRequired,
  
  // withContextMenu
  isContextMenuOpen: PropTypes.bool.isRequired,
  setContextMenuOpen: PropTypes.func.isRequired,

};

export default compose(
  connect(
    state => ({
      // state
    }),
    {
      pipelineElementSelected,
      pipelineElementMoved
    },
  ),
  withPipeline(),
  withElement(),
  withContextMenu,
  DragSource(ItemTypes.ELEMENT, dragSource, dragCollect),
  DropTarget([ItemTypes.ELEMENT], dropTarget, dropCollect),
)(PipelineElement);
