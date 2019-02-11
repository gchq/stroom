import * as React from "react";
import { connect } from "react-redux";
import { compose, withHandlers, withProps } from "recompose";
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
import { actionCreators as appChromeActionCreators } from "./redux";
import { StoreState as MenuItemsOpenStoreState } from "./redux/menuItemsOpenReducer";
import { GlobalStoreState } from "../../startup/reducers";
import { IconProp } from "@fortawesome/fontawesome-svg-core";
import { DocRefType, StyledComponentProps } from "../../types";
import { KeyDownState } from "../../lib/useKeyIsDown/useKeyIsDown";

const { menuItemOpened } = appChromeActionCreators;

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
  showCopyDialog: (docRefUuids: Array<string>, destinationUuid: string) => void;
  showMoveDialog: (docRefUuids: Array<string>, destinationUuid: string) => void;
}

interface ConnectState {
  isSelected: boolean;
  inFocus: boolean;
  areMenuItemsOpen: MenuItemsOpenStoreState;
}
interface ConnectDispatch {
  menuItemOpened: typeof menuItemOpened;
}

interface WithHandlers {
  onExpand: React.MouseEventHandler<HTMLDivElement>;
  onTitleClick: React.MouseEventHandler<HTMLDivElement>;
}

interface WithProps {
  style: React.CSSProperties;
  hasChildren: boolean;
  hasChildrenIcon: IconProp;
  isHeader: boolean;
}

export interface DndProps
  extends Props,
    ConnectDispatch,
    ConnectState,
    WithHandlers {}

interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    WithHandlers,
    DragCollectedProps,
    DropCollectedProps,
    WithProps {}

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
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    (
      { appChrome: { areMenuItemsOpen } },
      { selectedItems, focussedItem, menuItem: { key } }
    ) => {
      const isSelected: boolean = selectedItems
        .map((d: MenuItemType) => d.key)
        .includes(key);
      const inFocus: boolean = !!focussedItem && focussedItem.key === key;

      return {
        isSelected,
        inFocus,
        areMenuItemsOpen
      };
    },
    {
      menuItemOpened
    }
  ),
  withHandlers({
    onExpand: ({ menuItem, menuItemOpened, areMenuItemsOpen }) => (
      e: MouseEvent
    ) => {
      menuItemOpened(menuItem.key, !areMenuItemsOpen[menuItem.key]);
      e.preventDefault();
    },
    onTitleClick: ({ menuItem, menuItemOpened, areMenuItemsOpen }) => (
      e: MouseEvent
    ) => {
      menuItem.onClick();
      e.preventDefault();
    }
  }),
  DropTarget([DragDropTypes.DOC_REF_UUIDS], dropTarget, dropCollect),
  DragSource(DragDropTypes.DOC_REF_UUIDS, dragSource, dragCollect),
  withProps(
    ({
      menuItem,
      isOver,
      canDrop,
      inFocus,
      isSelected,
      depth,
      className,
      areMenuItemsOpen,
      isCollapsed
    }) => {
      const classNames = [];

      if (className) {
        classNames.push(className);
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

      return {
        style: { paddingLeft: `${depth * 0.7}rem` },
        className: classNames.join(" "),
        hasChildren,
        hasChildrenIcon: `folder${
          isShowingChildren ? "-open" : "-plus"
        }` as IconProp,
        isHeader: menuItem.key !== "stroom"
      };
    }
  )
);

let MenuItem = ({
  menuItem,
  hasChildren,
  hasChildrenIcon,
  isHeader,
  connectDropTarget,
  connectDragSource,
  onTitleClick,
  onExpand,
  className,
  style,
  isCollapsed
}: EnhancedProps) =>
  connectDragSource(
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

export default enhance(MenuItem);
