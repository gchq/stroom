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
import { Field, reduxForm } from 'redux-form';

import { Modal, Header, Button, Form } from 'semantic-ui-react';
import { DragSource, DropTarget } from 'react-dnd';

import { withElement } from './withElement';
import { withPipeline } from './withPipeline';
import { actionCreators } from './redux';
import { canMovePipelineElement } from './pipelineUtils';
import { ItemTypes } from './dragDropTypes';

const { pipelineElementSelected, pipelineElementMoved, pipelineElementAdded } = actionCreators;

const withNameNewElementModal = withState('newElementDefinition', 'setNewElementDefinition', undefined);

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
    switch (monitor.getItemType()) {
      case ItemTypes.ELEMENT:
        const newElementId = monitor.getItem().elementId;
        const { pipeline, asTree, elementId } = props;
        return canMovePipelineElement(pipeline, asTree, newElementId, elementId);
      case ItemTypes.PALLETE_ELEMENT:
        return true;
      default:
        return false;
    }
  },
  drop(props, monitor) {
    switch (monitor.getItemType()) {
      case ItemTypes.ELEMENT:
        const newElementId = monitor.getItem().elementId;
        const { elementId, pipelineId, pipelineElementMoved } = props;
        pipelineElementMoved(pipelineId, newElementId, elementId);
        break;
      case ItemTypes.PALLETE_ELEMENT:
        const newElementDefinition = monitor.getItem().element;
        const { setNewElementDefinition } = props;
        setNewElementDefinition(newElementDefinition);
        break;
      default:
        break;
    }
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
  pipelineElementAdded,
  selectedElementId,
  newElementForm,
  newElementDefinition,
  setNewElementDefinition
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
  if(selectedElementId === elementId){
    className += ' Pipeline-element__selected'
  }

  const onClick = () => pipelineElementSelected(pipelineId, elementId);
  const onConfirmNewElement = () => {
    pipelineElementAdded(pipelineId, elementId, newElementDefinition, newElementForm.values.name);
    setNewElementDefinition(undefined);
  }
  const onCancelNewElement = () => setNewElementDefinition(undefined);

  return compose(connectDragSource, connectDropTarget)(
    <div className={className} onClick={onClick}>
      <Modal
        size="tiny"
        open={!!newElementDefinition}
        onClose={onCancelNewElement}
      >
        <Header content='Add New Element' />
        <Modal.Content>
          <Form>
            <Form.Field>
              <label>Name</label>
              <Field name="name" component="input" type="text" placeholder="Name" />
            </Form.Field>
          </Form>
        </Modal.Content>
        <Modal.Actions>
          <Button positive content="Submit" onClick={onConfirmNewElement} />
          <Button negative content="Cancel" onClick={onCancelNewElement} />
        </Modal.Actions>
      </Modal>
      <img
        className="Pipeline-element__icon"
        alt="X"
        src={require(`./images/${elementDefinition.icon}`)}
      />
      <span className='Pipeline-element__type'>
      {elementId}
      </span>
    </div>);
};

PipelineElement.propTypes = {
  // Set by container
  pipelineId: PropTypes.string.isRequired,
  elementId: PropTypes.string.isRequired,

  selectedElementId: PropTypes.string,

  // withPipeline
  pipeline: PropTypes.object.isRequired,
  asTree: PropTypes.object.isRequired,

  // withElement
  element: PropTypes.object.isRequired,

  // redux form
  newElementForm: PropTypes.object,

  // Redux actions
  pipelineElementSelected: PropTypes.func.isRequired,
  pipelineElementMoved: PropTypes.func.isRequired,
  pipelineElementAdded: PropTypes.func.isRequired,

  // withNameNewElementModal
  newElementDefinition: PropTypes.object.isRequired,
  setNewElementDefinition: PropTypes.func.isRequired,
};

export default compose(
  connect(
    state => ({
      // state
      newElementForm : state.form.newElementName
    }),
    {
      pipelineElementSelected,
      pipelineElementMoved,
      pipelineElementAdded
    },
  ),
  withPipeline(),
  withElement(),
  withNameNewElementModal,
  DragSource(ItemTypes.ELEMENT, dragSource, dragCollect),
  DropTarget([ItemTypes.ELEMENT, ItemTypes.PALLETE_ELEMENT], dropTarget, dropCollect),
  reduxForm({ form: 'newElementName' }),
)(PipelineElement);
