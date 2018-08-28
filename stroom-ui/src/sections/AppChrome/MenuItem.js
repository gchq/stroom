import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { compose, withHandlers } from 'recompose';
import { DropTarget } from 'react-dnd';
import { DragSource } from 'react-dnd';
import { Icon } from 'semantic-ui-react/dist/commonjs';

import DocRefPropType from 'lib/DocRefPropType';
import { canMove } from 'lib/treeUtils';
import ItemTypes from 'components/FolderExplorer/dragDropTypes';
import { actionCreators as folderExplorerActionCreators } from 'components/FolderExplorer/redux';
import { actionCreators as appChromeActionCreators } from './redux';

const { menuItemOpened } = appChromeActionCreators;
const { prepareDocRefCopy, prepareDocRefMove } = folderExplorerActionCreators;

const dropTarget = {
  canDrop(
    {
      menuItem: { docRef },
    },
    monitor,
  ) {
    const { docRefs } = monitor.getItem();

    return !!docRef && docRefs.reduce((acc, curr) => acc && canMove(curr, docRef), true);
  },
  drop(
    {
      prepareDocRefCopy,
      prepareDocRefMove,
      menuItem: { docRef },
    },
    monitor,
  ) {
    const { docRefs, isCopy } = monitor.getItem();
    const docRefUuids = docRefs.map(d => d.uuid);

    if (isCopy) {
      prepareDocRefCopy(docRefUuids, docRef.uuid);
    } else {
      prepareDocRefMove(docRefUuids, docRef.uuid);
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

const dragSource = {
  canDrag(props) {
    return true;
  },
  beginDrag({ menuItem: { docRef }, keyIsDown: { Control, Meta } }) {
    return {
      docRefs: [docRef],
      isCopy: !!(Control || Meta),
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
  connect(
    (
      { keyIsDown, appChrome: { areMenuItemsOpen }, selectableItemListings },
      { listingId, menuItem: { key } },
    ) => {
      const selectableItemListing = selectableItemListings[listingId];
      let isSelected = false;
      if (selectableItemListing) {
        isSelected = selectableItemListing.selectedItems.filter(m => m.key === key).length === 1;
      }

      return { isSelected, keyIsDown, areMenuItemsOpen };
    },
    {
      prepareDocRefCopy,
      prepareDocRefMove,
      menuItemOpened,
    },
  ),
  withHandlers({
    onCaretClick: ({menuItem, menuItemOpened, areMenuItemsOpen}) => e => {
      menuItemOpened(menuItem.key, !areMenuItemsOpen[menuItem.key]);
      e.preventDefault();
    },
    onTitleClick: ({menuItem, menuItemOpened, areMenuItemsOpen}) => e => {
      menuItem.onClick();
    }
  }),
  DropTarget([ItemTypes.DOC_REF_UUIDS], dropTarget, dropCollect),
  DragSource(ItemTypes.DOC_REF_UUIDS, dragSource, dragCollect),
);

const MenuItem = ({
  menuItem,
  areMenuItemsOpen,
  menuItemOpened,
  depth,
  isSelected,
  connectDropTarget,
  connectDragSource,
  isOver,
  canDrop,
  onTitleClick, 
  onCaretClick
}) => {
  let className = `sidebar__menu-item ${menuItem.style}`;
  if (isOver) {
    className += ' dnd-over';
  }
  if (isOver) {
    if (canDrop) {
      className += ' can-drop';
    } else {
      className += ' cannot-drop';
    }
  }
  if (isSelected) {
    className += ' selected';
  }

  return connectDragSource(connectDropTarget(<div className={className} style={{ paddingLeft: `${depth * 0.7}rem` }}>
    {menuItem.children && menuItem.children.length > 0 ? (
      <Icon
        onClick={onCaretClick}
        name={`caret ${areMenuItemsOpen[menuItem.key] ? 'down' : 'right'}`}
      />
        ) : menuItem.key !== 'stroom' ? (
          <Icon />
        ) : (
          undefined
        )}
    <Icon name={menuItem.icon} />
    <span
      onClick={onTitleClick}
    >
      {menuItem.title}
    </span>
                                             </div>));
};

MenuItem.propTypes = {
  menuItem: PropTypes.shape({
    key: PropTypes.string.isRequired,
    title: PropTypes.string.isRequired,
    onClick: PropTypes.func.isRequired,
    icon: PropTypes.string.isRequired,
    style: PropTypes.string.isRequired,
  }).isRequired,
  docRef: DocRefPropType,
  listingId: PropTypes.string.isRequired,
  depth: PropTypes.number.isRequired,
};

export default enhance(MenuItem);
