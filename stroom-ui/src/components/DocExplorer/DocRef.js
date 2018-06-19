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

import { ItemTypes } from './dragDropTypes';
import { DragSource } from 'react-dnd';

import { actionCreators } from './redux';

import DocRefMenu from './DocRefMenu';

const { docRefSelected, docRefOpened } = actionCreators;

const withContextMenu = withState('isContextMenuOpen', 'setContextMenuOpen', false);

const dragSource = {
  canDrag(props) {
    return props.explorer.allowDragAndDrop;
  },
  beginDrag(props) {
    return {
      ...props.docRef,
    };
  },
};

function dragCollect(connect, monitor) {
  return {
    connectDragSource: connect.dragSource(),
    isDragging: monitor.isDragging(),
  };
}

const DocRef = ({
  explorerId,
  explorer,
  docRef,

  docRefSelected,
  docRefOpened,

  isContextMenuOpen,
  setContextMenuOpen,

  connectDragSource,
  isDragging,
}) => {
  // these are required to tell the difference between single/double clicks
  let timer = 0;
  const delay = 200;
  let prevent = false;

  const onSingleClick = () => {
    timer = setTimeout(() => {
      if (!prevent) {
        docRefSelected(explorerId, docRef);
      }
      prevent = false;
    }, delay);
  };

  const onDoubleClick = () => {
    clearTimeout(timer);
    prevent = true;
    docRefOpened(docRef);
  };

  const onRightClick = (e) => {
    setContextMenuOpen(true);
    e.preventDefault();
  };

  const isSelected = explorer.isSelected[docRef.uuid];

  let className = '';
  if (isDragging) {
    className += ' doc-ref__dragging';
  }
  if (isSelected) {
    className += ' doc-ref__selected';
  }
  if (isContextMenuOpen) {
    className += ' doc-ref__context-menu-open';
  }

  return connectDragSource(<div
    className={className}
    onContextMenu={onRightClick}
    onDoubleClick={onDoubleClick}
    onClick={onSingleClick}
  >
    <DocRefMenu
      explorerId={explorerId}
      docRef={docRef}
      isOpen={isContextMenuOpen}
      closeContextMenu={() => setContextMenuOpen(false)}
    />
    <span>
      <img className="doc-ref__icon" alt="X" src={require(`./images/${docRef.type}.svg`)} />
      {docRef.name}
    </span>
  </div>);
};

DocRef.propTypes = {
  // Props
  explorerId: PropTypes.string.isRequired,
  explorer: PropTypes.object.isRequired,
  docRef: PropTypes.object.isRequired,

  // Actions
  docRefSelected: PropTypes.func.isRequired,
  docRefOpened: PropTypes.func.isRequired,

  // withContextMenu
  isContextMenuOpen: PropTypes.bool.isRequired,
  setContextMenuOpen: PropTypes.func.isRequired,

  // React DnD
  connectDragSource: PropTypes.func.isRequired,
  isDragging: PropTypes.bool.isRequired,
};

export default compose(
  connect(
    (state, props) => ({
      // state
      explorer: state.explorerTree.explorers[props.explorerId]
    }),
    {
      docRefSelected,
      docRefOpened,
    },
  ),
  withContextMenu,
  DragSource(ItemTypes.DOC_REF, dragSource, dragCollect),
)(DocRef);
