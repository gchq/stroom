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
import { compose, withProps, withHandlers } from 'recompose';
import { connect } from 'react-redux';
import { DragSource, DropTarget } from 'react-dnd';
import { Image } from 'semantic-ui-react';

import { actionCreators } from './redux';
import { canMovePipelineElement } from './pipelineUtils';
import ItemTypes from './dragDropTypes';
import { isValidChildType } from './elementUtils';
import { getInitialValues } from './ElementDetails';

const {
  pipelineElementAddRequested,
  pipelineElementSelected,
  pipelineElementMoved,
  pipelineElementReinstated,
} = actionCreators;

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
    const { pipelineState, elementId, elementDefinition } = props;

    switch (monitor.getItemType()) {
      case ItemTypes.ELEMENT:
        const dropeeId = monitor.getItem().elementId;
        const dropeeDefinition = monitor.getItem().elementDefinition;
        const isValidChild = isValidChildType(elementDefinition, dropeeDefinition, 0);

        const isValid = canMovePipelineElement(
          pipelineState.pipeline,
          pipelineState.asTree,
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
      pipelineElementAddRequested,
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
          pipelineElementAddRequested(pipelineId, elementId, element);
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
    ({ pipelineEditor: { pipelineStates, elements } }, { pipelineId, elementId }) => {
      const pipelineState = pipelineStates[pipelineId];

      let selectedElementId;
      let element;
      let elementDefinition;

      if (pipelineState && pipelineState.pipeline) {
        selectedElementId = pipelineState.selectedElementId;
        element = pipelineState.pipeline.merged.elements.add.find(e => e.id === elementId);
        if (element) {
          elementDefinition = Object.values(elements.elements).find(e => e.type === element.type);
        }
      }

      return {
        // state
        pipelineState,
        selectedElementId,
        element,
        elementDefinition,
        elements,
      };
    },
    {
      // actions
      pipelineElementSelected,
      pipelineElementAddRequested,
      pipelineElementMoved,
      pipelineElementReinstated,
    },
  ),
  DragSource(ItemTypes.ELEMENT, dragSource, dragCollect),
  DropTarget([ItemTypes.ELEMENT, ItemTypes.PALLETE_ELEMENT], dropTarget, dropCollect),
  withProps(({
    isOver, canDrop, isDragging, selectedElementId, elementId, dndIsHappening,
  }) => {
    const classNames = ['Pipeline-element'];
    let isIconDisabled = false;

    if (isOver) {
      classNames.push('Pipeline-element__over');
    }
    if (isDragging) {
      classNames.push('Pipeline-element__dragging');
    }
    if (isOver) {
      if (canDrop) {
        classNames.push('Pipeline-element__over_can_drop');
      } else {
        isIconDisabled = true;
        classNames.push('Pipeline-element__cannot_drop');
      }
    } else if (canDrop) {
      classNames.push('Pipeline-element__not_over_can_drop');
    } else if (dndIsHappening) {
      isIconDisabled = true;
      classNames.push('Pipeline-element__cannot_drop');
    }

    if (selectedElementId === elementId) {
      classNames.push('Pipeline-element__selected');
    }

    return {
      className: classNames.join(' '),
      isIconDisabled,
    };
  }),
  withHandlers({
    onElementClick: ({
      onClick,
      element,
      elements: { elementProperties },
      pipelineState: { pipeline },
      pipelineElementSelected,
      pipelineId,
      elementId,
    }) => (e) => {
      onClick();
      // We need to get the initial values for this element and make sure they go into the state,
      // ready for redux-form to populate the new form.
      const thisElementTypeProperties = elementProperties[element.type];
      const thisElementProperties = pipeline.merged.properties.add.filter(property => property.element === element.id);
      const initialValues = getInitialValues(thisElementTypeProperties, thisElementProperties);
      return pipelineElementSelected(pipelineId, elementId, initialValues);
    },
  }),
);

const PipelineElement = ({
  pipelineId,
  elementId,
  onClick,
  connectDragSource,
  connectDropTarget,
  elementDefinition,
  isIconDisabled,
  className,
  onElementClick,
}) =>
  compose(connectDragSource, connectDropTarget)(<div className={`${className} raised-low borderless `} onClick={onElementClick}>
    <Image
      className="Pipeline-element__icon"
      alt="X"
      src={require(`./images/${elementDefinition.icon}`)}
      disabled={isIconDisabled}
      size="mini"
    />
    <button className="Pipeline-element__type">{elementId}</button>
  </div>);

PipelineElement.propTypes = {
  pipelineId: PropTypes.string.isRequired,
  elementId: PropTypes.string.isRequired,
  onClick: PropTypes.func,
  selectedElementId: PropTypes.string,
};

export default enhance(PipelineElement);
