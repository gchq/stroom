import * as React from "react";
import { compose } from "recompose";
import {
  DropTarget,
  DropTargetSpec,
  DropTargetCollector,
  DragSourceSpec,
  DragSourceCollector
} from "react-dnd";
import { DragSource } from "react-dnd";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";

import { canMove } from "../../lib/treeUtils";
import {
  DragDropTypes,
  DragObject,
  DragCollectedProps,
  DropCollectedProps
} from "../../components/FolderExplorer/dragDropTypes";
import { IconProp } from "@fortawesome/fontawesome-svg-core";
import { DocRefType, StyledComponentProps } from "../../types";
import { KeyDownState } from "../../lib/useKeyIsDown/useKeyIsDown";

export type MenuItemOpened = (name: string, isOpen: boolean) => void;

export type MenuItemsOpenState = {
  [s: string]: boolean;
};

export interface MenuItemType {
  key: string;
  title?: string;
  onClick: () => void;
  icon: IconProp;
  style: "doc" | "nav";
  skipInContractedMenu?: boolean;
  children?: Array<MenuItemType>;
  docRef?: DocRefType;
  parentDocRef?: DocRefType;
  isActive?: boolean;
}

interface Props extends StyledComponentProps {
  menuItem: MenuItemType;
  depth: number;
  isCollapsed?: boolean;
  selectedItems: Array<MenuItemType>;
  focussedItem?: MenuItemType;
  keyIsDown: KeyDownState;
  areMenuItemsOpen: MenuItemsOpenState;
  menuItemOpened: MenuItemOpened;
  showCopyDialog: (docRefUuids: Array<string>, destinationUuid: string) => void;
  showMoveDialog: (docRefUuids: Array<string>, destinationUuid: string) => void;
}

export interface DndProps extends Props {}

interface EnhancedProps extends Props, DragCollectedProps, DropCollectedProps {}

const dropTarget: DropTargetSpec<DndProps> = {
  canDrop({ menuItem: { docRef } }, monitor) {
    const { docRefs } = monitor.getItem();

    return (
      !!docRef &&
      docRefs.reduce(
        (acc: boolean, curr: DocRefType) => acc && canMove(curr, docRef),
        true
      )
    );
  },
  drop({ menuItem: { docRef }, showCopyDialog, showMoveDialog }, monitor) {
    const { docRefs, isCopy } = monitor.getItem();
    const docRefUuids = docRefs.map((d: DocRefType) => d.uuid);

    if (docRef) {
      if (isCopy) {
        showCopyDialog(docRefUuids, docRef.uuid);
      } else {
        showMoveDialog(docRefUuids, docRef.uuid);
      }
    }
  }
};

const dropCollect: DropTargetCollector<
  DropCollectedProps
> = function dropCollect(connect, monitor) {
  return {
    connectDropTarget: connect.dropTarget(),
    isOver: monitor.isOver(),
    canDrop: monitor.canDrop()
  };
};

const dragSource: DragSourceSpec<DndProps, DragObject> = {
  canDrag({ menuItem: { docRef } }) {
    return !!docRef;
  },
  beginDrag({ menuItem: { docRef }, keyIsDown: { Control, Meta } }) {
    return {
      docRefs: [docRef!],
      isCopy: !!(Control || Meta)
    };
  }
};

const dragCollect: DragSourceCollector<
  DragCollectedProps
> = function dragCollect(connect, monitor) {
  return {
    connectDragSource: connect.dragSource(),
    isDragging: monitor.isDragging()
  };
};

const enhance = compose<EnhancedProps, Props>(
  DropTarget([DragDropTypes.DOC_REF_UUIDS], dropTarget, dropCollect),
  DragSource(DragDropTypes.DOC_REF_UUIDS, dragSource, dragCollect)
);

let MenuItem = ({
  menuItem,
  isOver,
  canDrop,
  className: rawClassName,
  areMenuItemsOpen,
  depth,
  connectDropTarget,
  connectDragSource,
  isCollapsed,
  selectedItems,
  menuItemOpened,
  focussedItem
}: EnhancedProps) => {
  const isSelected: boolean = selectedItems
    .map((d: MenuItemType) => d.key)
    .includes(menuItem.key);
  const inFocus: boolean = !!focussedItem && focussedItem.key === menuItem.key;

  const onExpand: React.MouseEventHandler<HTMLDivElement> = (
    e: React.MouseEvent
  ) => {
    menuItemOpened(menuItem.key, !areMenuItemsOpen[menuItem.key]);
    e.preventDefault();
  };
  const onTitleClick: React.MouseEventHandler<HTMLDivElement> = (
    e: React.MouseEvent
  ) => {
    menuItem.onClick();
    e.preventDefault();
  };

  const classNames = [];

  if (rawClassName) {
    classNames.push(rawClassName);
  }

  classNames.push("sidebar__menu-item");
  classNames.push(menuItem.style);

  if (isOver) {
    classNames.push("dnd-over");
  }
  if (isOver) {
    if (canDrop) {
      classNames.push("can-drop");
    } else {
      classNames.push("cannot-drop");
    }
  }
  if (inFocus) {
    classNames.push("inFocus");
  }
  if (isSelected) {
    classNames.push("selected");
  }

  const hasChildren = menuItem.children && menuItem.children.length > 0;
  const isShowingChildren = areMenuItemsOpen[menuItem.key];
  if (hasChildren && isShowingChildren) {
    classNames.push("has-children--open");
  }

  if (menuItem.isActive) {
    classNames.push("is-active");
  }

  const style = { paddingLeft: `${depth * 0.7}rem` };
  const className = classNames.join(" ");

  const hasChildrenIcon = `folder${
    isShowingChildren ? "-open" : "-plus"
  }` as IconProp;
  //const isHeader = menuItem.key !== "stroom";

  return connectDragSource(
    connectDropTarget(
      <div className={className} style={style}>
        {hasChildren ? (
          <div className="menu-item__menu-icon" onClick={onExpand}>
            <FontAwesomeIcon size="lg" icon={hasChildrenIcon} />
          </div>
        ) : (
          <div className="menu-item__menu-icon">
            <FontAwesomeIcon size="lg" icon={menuItem.icon} />
          </div>
        )}
        {isCollapsed ? (
          undefined
        ) : (
          <span onClick={onTitleClick} className="menu-item__text">
            {menuItem.title}
          </span>
        )}
      </div>
    )
  );
};

export default enhance(MenuItem);
