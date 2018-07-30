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

import { compose } from 'recompose';
import { connect } from 'react-redux';
import { withRouter } from 'react-router-dom';

import ItemTypes from './dragDropTypes';
import { DragSource } from 'react-dnd';

import { actionCreators as docExplorerActionCreators } from './redux/explorerTreeReducer';

import DocRefMenu from './DocRefMenu';
import ClickCounter from 'lib/ClickCounter';
import { openDocRef } from 'prototypes/RecentItems';

const { docRefSelected, docRefContextMenuOpened, docRefContextMenuClosed } = docExplorerActionCreators;

const dragSource = {
  canDrag(props) {
    return true;
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

const enhance = compose(
  withRouter,
  connect(
    (state, props) => ({
      // state
      explorer: state.docExplorer.explorerTree.explorers[props.explorerId],
    }),
    {
      docRefSelected,
      openDocRef,
      docRefContextMenuOpened,
      docRefContextMenuClosed
    },
  ),
  DragSource(ItemTypes.DOC_REF, dragSource, dragCollect),
);

const DocRef = ({
  explorerId,
  explorer,
  docRef,
  history, 
  docRefSelected,
  openDocRef,
  docRefContextMenuOpened,
  docRefContextMenuClosed,
  connectDragSource,
  isDragging,
}) => {
  // these are required to tell the difference between single/double clicks
  const clickCounter = new ClickCounter()
    .withOnSingleClick(({appendSelection, contiguousSelection}) => docRefSelected(explorerId, docRef, appendSelection, contiguousSelection))
    .withOnDoubleClick(() => openDocRef(history, docRef))

  const onRightClick = (e) => {
    docRefContextMenuOpened(explorerId, docRef);
    e.preventDefault();
  };

  const isSelected = explorer.isSelected[docRef.uuid];
  const { contentMenuDocRef } = explorer;
  const isContextMenuOpen = (!!contentMenuDocRef && contentMenuDocRef.uuid === docRef.uuid);

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
    onDoubleClick={() => clickCounter.onDoubleClick()}
    onClick={e => clickCounter.onSingleClick({
      appendSelection: e.ctrlKey || e.metaKey,
      contiguousSelection: e.shiftKey
    })}
  >
    <DocRefMenu
      explorerId={explorerId}
      docRef={docRef}
      isOpen={isContextMenuOpen}
      closeContextMenu={() => docRefContextMenuClosed(explorerId)}
    />
    <span>
      <img className="doc-ref__icon" alt="X" src={require(`./images/${docRef.type}.svg`)} />
      {docRef.name}
    </span>
  </div>);
};

DocRef.propTypes = {
  explorerId: PropTypes.string.isRequired,
  docRef: PropTypes.object.isRequired,
};

export default enhance(DocRef);
