import React from 'react';
import PropTypes from 'prop-types';

import { Button } from 'semantic-ui-react';
import { DragSource } from 'react-dnd';

import { ItemTypes } from '../dragDropTypes';

const dragSource = {
  canDrag(props) {
    return true;
  },
  beginDrag(props) {
    return {
      element: props.element,
    };
  },
};

function dragCollect(connect, monitor) {
  return {
    connectDragSource: connect.dragSource(),
    isDragging: monitor.isDragging(),
  };
}

const NewElement = ({ connectDragSource, isDragging, element }) =>
  connectDragSource(<div className="element-palette-element">
    <div className="element-palette-element__button-contents">
      <img className="element-palette__icon" alt="X" src={require(`../images/${element.icon}`)} />
      <span className="element-palette__type">{element.type}</span>
    </div>
                    </div>);

NewElement.propTypes = {
  element: PropTypes.object.isRequired,
};

export default DragSource(ItemTypes.PALLETE_ELEMENT, dragSource, dragCollect)(NewElement);
