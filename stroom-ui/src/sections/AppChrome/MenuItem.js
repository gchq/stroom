import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { compose, withHandlers, withProps } from 'recompose';
import { DropTarget } from 'react-dnd';
import { DragSource } from 'react-dnd';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

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
      listingId,
      prepareDocRefCopy,
      prepareDocRefMove,
      menuItem: { docRef },
    },
    monitor,
  ) {
    const { docRefs, isCopy } = monitor.getItem();
    const docRefUuids = docRefs.map(d => d.uuid);

    if (isCopy) {
      prepareDocRefCopy(listingId, docRefUuids, docRef.uuid);
    } else {
      prepareDocRefMove(listingId, docRefUuids, docRef.uuid);
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
      const { selectedItems = [], focussedItem } = selectableItemListings[listingId] || {};
      const isSelected = selectedItems.map(d => d.key).includes(key);
      const inFocus = focussedItem && focussedItem.key === key;

      return {
        isSelected, inFocus, keyIsDown, areMenuItemsOpen,
      };
    },
    {
      prepareDocRefCopy,
      prepareDocRefMove,
      menuItemOpened,
    },
  ),
  withHandlers({
    onCaretClick: ({ menuItem, menuItemOpened, areMenuItemsOpen }) => (e) => {
      menuItemOpened(menuItem.key, !areMenuItemsOpen[menuItem.key]);
      e.preventDefault();
    },
    onTitleClick: ({ menuItem, menuItemOpened, areMenuItemsOpen }) => (e) => {
      menuItem.onClick();
    },
  }),
  DropTarget([ItemTypes.DOC_REF_UUIDS], dropTarget, dropCollect),
  DragSource(ItemTypes.DOC_REF_UUIDS, dragSource, dragCollect),
  withProps(({
    menuItem, isOver, canDrop, inFocus, isSelected, depth,
  }) => {
    const classNames = [];

    classNames.push('sidebar__menu-item');
    classNames.push(menuItem.style);

    if (isOver) {
      classNames.push('dnd-over');
    }
    if (isOver) {
      if (canDrop) {
        classNames.push('can-drop');
      } else {
        classNames.push('cannot-drop');
      }
    }
    if (inFocus) {
      classNames.push('inFocus');
    }
    if (isSelected) {
      classNames.push('selected');
    }

    return {
      style: { paddingLeft: `${depth * 0.7}rem` },
      className: classNames.join(' '),
    };
  }),
);

let MenuItem = ({
  menuItem,
  areMenuItemsOpen,
  menuItemOpened,
  connectDropTarget,
  connectDragSource,
  onTitleClick,
  onCaretClick,
  className,
  style,
}) =>
  connectDragSource(connectDropTarget(<div className={className} style={style}>
    {menuItem.children && menuItem.children.length > 0 ? (
      <FontAwesomeIcon
        onClick={onCaretClick}
        icon={`caret-${areMenuItemsOpen[menuItem.key] ? 'down' : 'right'}`}
      />
        ) : menuItem.key !== 'stroom' ? (
          <div className="AppChrome__MenuItemIcon" />
        ) : (
          undefined
        )}
    <FontAwesomeIcon icon={menuItem.icon} />
    <span onClick={onTitleClick}>{menuItem.title}</span>
                                      </div>));

MenuItem = enhance(MenuItem);

MenuItem.propTypes = {
  listingId: PropTypes.string.isRequired,
  menuItem: PropTypes.shape({
    key: PropTypes.string.isRequired,
    title: PropTypes.string.isRequired,
    onClick: PropTypes.func.isRequired,
    icon: PropTypes.string.isRequired,
    style: PropTypes.string.isRequired,
  }).isRequired,
  docRef: DocRefPropType,
  depth: PropTypes.number.isRequired,
};

export default MenuItem;
