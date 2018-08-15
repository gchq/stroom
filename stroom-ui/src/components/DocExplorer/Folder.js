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

import { canMove } from '../../lib/treeUtils';
import ItemTypes from './dragDropTypes';
import { DragSource, DropTarget } from 'react-dnd';

import { Icon } from 'semantic-ui-react';

import ClickCounter from 'lib/ClickCounter';
import DocRef from './DocRef';
import { actionCreators } from './redux';

const {
  folderOpenToggled,
  docRefSelected,
  docRefContextMenuOpened,
  docRefContextMenuClosed,
  prepareDocRefCopy,
  prepareDocRefMove,
} = actionCreators;

const dragSource = {
  canDrag(props) {
    return true;
  },
  beginDrag(props) {
    return {
      docRef: props.folder,
      isCopy: !!(props.keyIsDown.Control || props.keyIsDown.Meta),
    };
  },
};

function dragCollect(connect, monitor) {
  return {
    connectDragSource: connect.dragSource(),
    isDragging: monitor.isDragging(),
    isCopy: monitor.getItem() ? monitor.getItem().isCopy : false
  };
}

const dropTarget = {
  canDrop(props, monitor) {
    return canMove(monitor.getItem().docRef, props.folder);
  },
  drop(props, monitor) {
    const {
      docRef, isCopy
    } = monitor.getItem();
    
    if (isCopy) {
      props.prepareDocRefCopy([docRef.uuid], props.folder.uuid)
    } else {
      props.prepareDocRefMove([docRef.uuid], props.folder.uuid);
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

const enhance = compose(
  connect(
    (
      {
        keyIsDown,
        docExplorer: { explorers },
      },
      { explorerId },
    ) => ({
      keyIsDown,
      explorer: explorers[explorerId],
    }),
    {
      folderOpenToggled,
      docRefSelected,
      docRefContextMenuOpened,
      docRefContextMenuClosed,
      prepareDocRefCopy,
      prepareDocRefMove,
    },
  ),
  DragSource(ItemTypes.FOLDER, dragSource, dragCollect),
  DropTarget([ItemTypes.FOLDER, ItemTypes.DOC_REF], dropTarget, dropCollect),
);

const _Folder = ({
  connectDragSource,
  isDragging,
  isCopy,
  connectDropTarget,
  isOver,
  canDrop,
  explorerId,
  explorer,
  folder,
  folderOpenToggled,
  docRefSelected,
  docRefContextMenuOpened,
  docRefContextMenuClosed,
  keyIsDown,
}) => {
  const thisIsOpen = !!explorer.isFolderOpen[folder.uuid];
  const hasChildren = folder.children && folder.children.length > 0;
  const folderIcon = thisIsOpen ? 'folder open' : 'folder';
  const caretIcon = hasChildren ? (thisIsOpen ? 'caret down' : 'caret right') : undefined;
  const isSelected = explorer.isSelected[folder.uuid];
  const { contentMenuDocRef } = explorer;
  const isContextMenuOpen = !!contentMenuDocRef && contentMenuDocRef.uuid === folder.uuid;
  const isPartOfContextMenuSelection = !!contentMenuDocRef && isSelected;

  let className = '';
  if (isOver) {
    className += ' folder__over';
  }
  if (isDragging) {
    className += ' folder__dragging ';
  }
  if (isOver) {
    if (canDrop) {
      className += ' folder__over_can_drop';
    } else {
      className += ' folder__over_cannot_drop';
    }
  }
  if (isPartOfContextMenuSelection) {
    className += ' doc-ref__context-menu-open';
  }
  if (isSelected) {
    className += ' doc-ref__selected';
  }

  const clickCounter = new ClickCounter()
    .withOnSingleClick(({ appendSelection, contiguousSelection }) =>
      docRefSelected(explorerId, folder, appendSelection, contiguousSelection))
    .withOnDoubleClick(() => folderOpenToggled(explorerId, folder));

  const onRightClick = (e) => {
    if (!isSelected) {
      docRefSelected(explorerId, folder, false, false);
    }
    docRefContextMenuOpened(explorerId, folder);
    e.preventDefault();
  };

  return (
    <div>
      {connectDragSource(connectDropTarget(<span className={className} onContextMenu={onRightClick}>
        <span
          onClick={e =>
                clickCounter.onSingleClick({
                  appendSelection: e.ctrlKey || e.metaKey,
                  contiguousSelection: e.shiftKey,
                })
              }
          onDoubleClick={() => clickCounter.onDoubleClick()}
        >
          <Icon name={caretIcon} onClick={() => clickCounter.onDoubleClick()} />
          <Icon name={folderIcon} />
          <span>{folder.name} {isOver && canDrop && (isCopy ? 'copy' : 'move')}</span>
        </span>
      </span>))}
      {thisIsOpen && (
        <div className="folder__children">
          {hasChildren ? folder.children
            .filter(c => !!explorer.inTypeFilter[c.uuid])
            .map(c =>
                (c.type === 'Folder' ? (
                  <Folder key={c.uuid} explorerId={explorerId} folder={c} />
                ) : (
                  <DocRef key={c.uuid} explorerId={explorerId} docRef={c} />
                )))
            : undefined
            }
        </div>
      )}
    </div>
  );
};

const Folder = enhance(_Folder);

Folder.propTypes = {
  explorerId: PropTypes.string.isRequired,
  folder: PropTypes.object.isRequired,
};

export default Folder;
