import React from 'react';
import PropTypes from 'prop-types';

import { compose } from 'recompose';
import { Menu, Icon, Image } from 'semantic-ui-react';
import { DragSource } from 'react-dnd';

import { ItemTypes } from './dragDropTypes';

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

const PaletteElement = ({ element, connectDragSource, isDragging }) => (
  <Menu.Item name={element.type}>
    <Icon>
      <Image size="mini" src={require(`./images/${element.icon}`)} />
    </Icon>
    {compose(connectDragSource)(<button className="Pipeline-editor__element-button">{element.type}</button>)}
  </Menu.Item>
);

PaletteElement.propTypes = {
  element: PropTypes.object.isRequired,
};

export default compose(DragSource(ItemTypes.PALLETE_ELEMENT, dragSource, dragCollect))(PaletteElement);
