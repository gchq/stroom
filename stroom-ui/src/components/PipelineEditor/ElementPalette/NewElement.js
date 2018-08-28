import React from 'react';
import PropTypes from 'prop-types';

import { compose, withState, withProps } from 'recompose';
import { DragSource } from 'react-dnd';
import ItemTypes from '../dragDropTypes';

const withFocus = withState('hasFocus', 'setHasFocus', false);

const dragSource = {
  canDrag(props) {
    return true;
  },
  beginDrag(props) {
    return {
      element: props.element,
      recycleData: props.recycleData,
    };
  },
};

const dragCollect = (connect, monitor) => ({
  connectDragSource: connect.dragSource(),
  isDragging: monitor.isDragging(),
});

const enhance = compose(
  withFocus,
  withProps(({ elementWithData }) => ({
    element: elementWithData.element,
    recycleData: elementWithData.recycleData,
  })),
  DragSource(ItemTypes.PALLETE_ELEMENT, dragSource, dragCollect),
);

const NewElement = ({
  connectDragSource,
  isDragging,
  element,
  recycleData,
  hasFocus,
  setHasFocus,
}) =>
  connectDragSource(<div className={`element-palette-element raised-low borderless ${hasFocus ? 'focus' : 'no-focus'}`}>
    <div className="element-palette-element__button-contents">
      <img className="element-palette__icon" alt="X" src={require(`../images/${element.icon}`)} />
      <button
        className="element-palette__type"
        onFocus={() => setHasFocus(true)}
        onBlur={() => setHasFocus(false)}
      >
        {recycleData ? recycleData.id : element.type}
      </button>
    </div>
                    </div>);

NewElement.propTypes = {
  elementWithData: PropTypes.object.isRequired,
};

export default enhance(NewElement);
