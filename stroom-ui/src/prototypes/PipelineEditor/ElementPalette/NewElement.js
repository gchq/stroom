import React from 'react';
import PropTypes from 'prop-types';

import { compose, withState } from 'recompose';
import { DragSource } from 'react-dnd';
import { ItemTypes } from '../dragDropTypes';

const withFocus = withState('hasFocus', 'setHasFocus', false);

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

const NewElement = ({
  connectDragSource, isDragging, element, hasFocus, setHasFocus,
}) =>
  connectDragSource(<div className={`element-palette-element ${hasFocus ? 'focus' : 'no-focus'}`}>
    <div className="element-palette-element__button-contents">
      <img className="element-palette__icon" alt="X" src={require(`../images/${element.icon}`)} />
      <button
        className="element-palette__type"
        onFocus={() => setHasFocus(true)}
        onBlur={() => setHasFocus(false)}
      >
        {element.type}
      </button>
    </div>
                    </div>);

NewElement.propTypes = {
  element: PropTypes.object.isRequired,

  // withFocus
  hasFocus: PropTypes.bool.isRequired,
  setHasFocus: PropTypes.func.isRequired,
};

export default compose(DragSource(ItemTypes.PALLETE_ELEMENT, dragSource, dragCollect), withFocus)(NewElement);
