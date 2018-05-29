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
import React, { Component } from 'react';
import PropTypes from 'prop-types';

import { connect } from 'react-redux'

import { DragSource, DropTarget } from 'react-dnd';

import { withPipeline } from './withPipeline';

import {
  pipelineElementSelected,
  pipelineElementMoved,
  openPipelineElementContextMenu,
  closePipelineElementContextMenu
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
      elementId : props.elementId
    };
  }
};

function dragCollect(connect, monitor) {
    return {
        connectDragSource: connect.dragSource(),
        isDragging: monitor.isDragging()
    }
}

const dropTarget = {
    canDrop(props, monitor) {
        return canMovePipelineElement(props.pipeline, props.asTree, monitor.getItem().elementId, props.elementId)
    },
    drop(props, monitor) {
        props.pipelineElementMoved(props.pipelineId, monitor.getItem().elementId, props.elementId);
    }
}

function dropCollect(connect, monitor) {
    return {
      connectDropTarget: connect.dropTarget(),
      isOver: monitor.isOver(),
      canDrop: monitor.canDrop()
    };
}

class PipelineElement extends Component {
  static propTypes = {
    pipelineId: PropTypes.string.isRequired,
    pipeline: PropTypes.object.isRequired,
    asTree : PropTypes.object.isRequired,
    elementId: PropTypes.string.isRequired,
    contextMenuElementId : PropTypes.string,

    pipelineElementSelected : PropTypes.func.isRequired
  };

  onSingleClick() {
    this.props.pipelineElementSelected(this.props.pipelineId, this.props.elementId);
  }

  onRightClick(e) {
      this.props.openPipelineElementContextMenu(this.props.pipelineId, this.props.elementId);
      e.preventDefault();
  }

  render() {
    const {
      connectDragSource,
      isDragging,
      connectDropTarget,
      isOver,
      canDrop, 
      pipelineId,
      elementId,
      contextMenuElementId
    } = this.props;

    let isContextMenuOpen = !!contextMenuElementId && contextMenuElementId === elementId;

    let className='Pipeline-element';
    if (isOver) {
      className += ' Pipeline-element__over';
    }
    if (isDragging) {
        className += ' Pipeline-element__dragging '   
    }
    if (isOver) {
        if (canDrop) {
            className += ' Pipeline-element__over_can_drop';
        } else {
            className += ' Pipeline-element__over_cannot_drop';
        }
    }

    return (
      connectDragSource(
        connectDropTarget(
          <span>
            <span className={className}
              onClick={this.onSingleClick.bind(this)}
              onContextMenu={this.onRightClick.bind(this)}
              >
              
              {elementId}
            </span>
            <span className='Pipeline-element__context-menu'>
              <ElementMenu 
                pipelineId={pipelineId}
                elementId={elementId}
                isOpen={isContextMenuOpen}
                />
            </span>
          </span>
        )
      )
    );
  }
}

export default connect(
  (state) => ({
      // state
  }),
  {
    pipelineElementSelected,
    pipelineElementMoved,
    openPipelineElementContextMenu,
    closePipelineElementContextMenu
  }
)(withPipeline()(DragSource(ItemTypes.ELEMENT, dragSource, dragCollect)(
  DropTarget([ItemTypes.ELEMENT], dropTarget, dropCollect)(
    PipelineElement
  )
)));
