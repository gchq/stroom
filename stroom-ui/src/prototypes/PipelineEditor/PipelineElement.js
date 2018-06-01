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

import { compose } from 'redux';
import { connect } from 'react-redux';

import { DragSource, DropTarget } from 'react-dnd';

import { withElement } from './withElement';
import { withPipeline } from './withPipeline';

import {
  pipelineElementSelected,
  pipelineElementMoved,
  openPipelineElementContextMenu,
  closePipelineElementContextMenu,
} from './redux';

import ElementMenu from './ElementMenu';

import { canMovePipelineElement } from './pipelineUtils';

import { ItemTypes } from './dragDropTypes';

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
  contextMenuElementId,
  pipelineElementSelected,
  openPipelineElementContextMenu,
}) => {
  const isContextMenuOpen = !!contextMenuElementId && contextMenuElementId === elementId;

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
    openPipelineElementContextMenu(pipelineId, elementId);
    e.preventDefault();
  };

  return connectDragSource(connectDropTarget(<span>
    <span className={className} onClick={onSingleClick} onContextMenu={onRightClick}>
      <img
        className="Pipeline-element__icon"
        src={require(`./images/${elementDefinition.icon}`)}
      />
      {elementId}
    </span>
    <span className="Pipeline-element__context-menu">
      <ElementMenu pipelineId={pipelineId} elementId={elementId} isOpen={isContextMenuOpen} />
    </span>
                                             </span>));
};

PipelineElement.propTypes = {
  pipelineId: PropTypes.string.isRequired,
  pipeline: PropTypes.object.isRequired,
  element: PropTypes.object.isRequired,
  asTree: PropTypes.object.isRequired,
  elementId: PropTypes.string.isRequired,
  contextMenuElementId: PropTypes.string,

  pipelineElementSelected: PropTypes.func.isRequired,
};

export default compose(
  connect(
    state => ({
      // state
    }),
    {
      pipelineElementSelected,
      pipelineElementMoved,
      openPipelineElementContextMenu,
      closePipelineElementContextMenu,
    },
  ),
  withPipeline(),
  withElement(),
  DragSource(ItemTypes.ELEMENT, dragSource, dragCollect),
  DropTarget([ItemTypes.ELEMENT], dropTarget, dropCollect),
)(PipelineElement);
