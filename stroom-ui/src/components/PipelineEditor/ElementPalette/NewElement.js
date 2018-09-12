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
import { compose, withState, withProps } from 'recompose';
import { DragSource } from 'react-dnd';

import Button from 'components/Button';
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
      <Button
        className="element-palette__type"
        onFocus={() => setHasFocus(true)}
        onBlur={() => setHasFocus(false)}
        text={recycleData ? recycleData.id : element.type}
      />
    </div>
  </div>);

NewElement.propTypes = {
  elementWithData: PropTypes.object.isRequired,
};

export default enhance(NewElement);
