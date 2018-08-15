import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { compose } from 'recompose';
import { DropTarget } from 'react-dnd';
import { Icon } from 'semantic-ui-react';

import DocRefPropType from 'lib/DocRefPropType';
import { canMove } from 'lib/treeUtils';
import ItemTypes from 'components/DocRefListing/dragDropTypes';
import { actionCreators as folderExplorerActionCreators } from 'components/FolderExplorer/redux';

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
  drop({prepareDocRefCopy, prepareDocRefMove, menuItem: { docRef },}, monitor) {
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

const enhance = compose(
  connect(() => {}, {
    prepareDocRefCopy,
    prepareDocRefMove,
  }),
  DropTarget([ItemTypes.DOC_REF_UUIDS], dropTarget, dropCollect),
);

const MenuItem = ({
  menuItem,
  menuItemsOpen,
  menuItemOpened,
  depth,
  connectDropTarget,
  isOver,
  canDrop,
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

  return connectDropTarget(<div className={className} style={{ paddingLeft: `${depth * 0.7}rem` }}>
    {menuItem.children && menuItem.children.length > 0 ? (
      <Icon
        onClick={(e) => {
            menuItemOpened(menuItem.key, !menuItemsOpen[menuItem.key]);
            e.preventDefault();
          }}
        name={`caret ${menuItemsOpen[menuItem.key] ? 'down' : 'right'}`}
      />
      ) : menuItem.key !== 'stroom' ? (
        <Icon />
      ) : (
        undefined
      )}
    <Icon name={menuItem.icon} />
    <span
      onClick={() => {
          if (menuItem.children) {
            menuItemOpened(menuItem.key, !menuItemsOpen[menuItem.key]);
          }
          menuItem.onClick();
        }}
    >
      {menuItem.title}
    </span>
  </div>);
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
  menuItemsOpen: PropTypes.object.isRequired,
  menuItemOpened: PropTypes.func.isRequired,
  depth: PropTypes.number.isRequired,
};

export default enhance(MenuItem);
