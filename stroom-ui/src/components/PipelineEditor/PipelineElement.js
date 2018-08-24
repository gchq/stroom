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

import { compose, withState, renderComponent, branch } from 'recompose';
import { connect } from 'react-redux';

import { DragSource, DropTarget } from 'react-dnd';

import { Image, Loader } from 'semantic-ui-react';

import AddElementModal from './AddElementModal';
import { actionCreators } from './redux';
import { canMovePipelineElement } from './pipelineUtils';
import ItemTypes from './dragDropTypes';
import { isValidChildType } from './elementUtils';

import { getInitialValues } from './ElementDetails';

const { pipelineElementSelected, pipelineElementMoved, pipelineElementReinstated } = actionCreators;

const withFocus = withState('hasFocus', 'setHasFocus', false);
const withNameNewElementModal = withState(
  'newElementDefinition',
  'setNewElementDefinition',
  undefined,
);

const dragSource = {
  canDrag(props) {
    return true;
  },
  beginDrag(props) {
    return {
      pipelineId: props.pipelineId,
      elementId: props.elementId,
      elementDefinition: props.elementDefinition,
    };
  },
};

const dragCollect = (connect, monitor) => ({
  connectDragSource: connect.dragSource(),
  isDragging: monitor.isDragging(),
});

const dropTarget = {
  canDrop(props, monitor) {
    const { pipeline, elementId, elementDefinition } = props;

    switch (monitor.getItemType()) {
      case ItemTypes.ELEMENT:
        const dropeeId = monitor.getItem().elementId;
        const dropeeDefinition = monitor.getItem().elementDefinition;
        const isValidChild = isValidChildType(elementDefinition, dropeeDefinition, 0);

        const isValid = canMovePipelineElement(
          pipeline.pipeline,
          pipeline.asTree,
          dropeeId,
          elementId,
        );

        return isValidChild && isValid;
      case ItemTypes.PALLETE_ELEMENT:
        const dropeeType = monitor.getItem().element;
        if (dropeeType) {
          const isValidChild = isValidChildType(elementDefinition, dropeeType, 0);
          return isValidChild;
        }
        return true;

      default:
        return false;
    }
  },
  drop(props, monitor) {
    const {
      elementId,
      pipelineId,
      pipelineElementMoved,
      setNewElementDefinition,
      pipelineElementReinstated,
    } = props;

    switch (monitor.getItemType()) {
      case ItemTypes.ELEMENT: {
        const newElementId = monitor.getItem().elementId;
        pipelineElementMoved(pipelineId, newElementId, elementId);
        break;
      }
      case ItemTypes.PALLETE_ELEMENT: {
        const { element, recycleData } = monitor.getItem();

        if (recycleData) {
          pipelineElementReinstated(pipelineId, elementId, recycleData);
        } else {
          setNewElementDefinition(element);
        }
        break;
      }
      default:
        break;
    }
  },
};

const dropCollect = (connect, monitor) => ({
  connectDropTarget: connect.dropTarget(),
  isOver: monitor.isOver(),
  canDrop: monitor.canDrop(),
  dndIsHappening: monitor.getItem() !== null,
});

const enhance = compose(
  connect(
    (state, props) => {
      const pipeline = state.pipelineEditor.pipelines[props.pipelineId];
      const elements = state.pipelineEditor.elements;

      let selectedElementId;
      let element;
      let elementDefinition;

      if (pipeline && pipeline.pipeline) {
        selectedElementId = pipeline.selectedElementId;
        element = pipeline.pipeline.merged.elements.add.find(e => e.id === props.elementId);
        if (element) {
          elementDefinition = Object.values(elements.elements).find(e => e.type === element.type);
        }
      }

      return {
        // state
        pipeline,
        selectedElementId,
        element,
        elementDefinition,
        elements,
      };
    },
    {
      // actions
      pipelineElementSelected,
      pipelineElementMoved,
      pipelineElementReinstated,
    },
  ),
  branch(
    ({ pipeline }) => !pipeline,
    renderComponent(() => <Loader active>Loading Pipeline</Loader>),
  ),
  branch(({ element }) => !element, renderComponent(() => <Loader active>Loading Element</Loader>)),
  withNameNewElementModal,
  withFocus,
  DragSource(ItemTypes.ELEMENT, dragSource, dragCollect),
  DropTarget([ItemTypes.ELEMENT, ItemTypes.PALLETE_ELEMENT], dropTarget, dropCollect),
);

const PipelineElement = ({
  pipelineId,
  elementId,
  onClick,
  connectDragSource,
  isDragging,
  connectDropTarget,
  isOver,
  canDrop,
  dndIsHappening,
  pipeline,
  element,
  elements,
  elementDefinition,
  selectedElementId,
  newElementForm,
  pipelineElementSelected,
  newElementDefinition,
  setNewElementDefinition,
  hasFocus,
  setHasFocus,
}) => {
  let isIconDisabled = false;
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
      isIconDisabled = true;
      className += ' Pipeline-element__cannot_drop';
    }
  } else if (canDrop) {
    className += ' Pipeline-element__not_over_can_drop';
  } else if (dndIsHappening) {
    isIconDisabled = true;
    className += ' Pipeline-element__cannot_drop';
  }

  if (selectedElementId === elementId) {
    className += ' Pipeline-element__selected';
  }

  if (hasFocus) {
    className += ' focus';
  }

  const handleClick = () => {
    onClick();
    // We need to get the initial values for this element and make sure they go into the state,
    // ready for redux-form to populate the new form.
    const elementTypeProperties = elements.elementProperties[element.type];
    const elementProperties = pipeline.pipeline.merged.properties.add.filter(property => property.element === element.id);
    const initalValues = getInitialValues(elementTypeProperties, elementProperties);
    return pipelineElementSelected(pipelineId, elementId, initalValues);
  };

  return compose(connectDragSource, connectDropTarget)(<div className={className} onClick={handleClick}>
    <AddElementModal
      {...{
          setNewElementDefinition,
          newElementDefinition,
          pipelineId,
          elementId,
        }}
    />
    <Image
      className="Pipeline-element__icon"
      alt="X"
      src={require(`./images/${elementDefinition.icon}`)}
      disabled={isIconDisabled}
      size="mini"
    />
    <button
      onFocus={() => setHasFocus(true)}
      onBlur={() => setHasFocus(false)}
      className="Pipeline-element__type"
    >
      {elementId}
    </button>
  </div>);
};

PipelineElement.propTypes = {
  pipelineId: PropTypes.string.isRequired,
  elementId: PropTypes.string.isRequired,
  onClick: PropTypes.func,
  selectedElementId: PropTypes.string,
};

export default enhance(PipelineElement);
