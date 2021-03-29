import * as React from "react";
import {
  DropTarget,
  DropTargetSpec,
  DropTargetCollector,
  DragSourceSpec,
  DragSourceCollector,
  DragSource,
} from "react-dnd";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";

import { canMove } from "lib/treeUtils/treeUtils";
import {
  DragDropTypes,
  DragObject,
  DragCollectedProps,
  DropCollectedProps,
} from "components/DocumentEditors/FolderExplorer/types";
import { IconProp } from "@fortawesome/fontawesome-svg-core";
import { KeyDownState } from "lib/useKeyIsDown";
import { MenuItemToggled, MenuItemType, MenuItemsOpenState } from "./types";
import { DocRefType } from "components/DocumentEditors/useDocumentApi/types/base";

interface Props {
  className?: string;
  menuItem: MenuItemType;
  activeMenuItem: string;
  depth: number;
  isCollapsed?: boolean;
  selectedItems: string[];
  highlightedItem?: MenuItemType;
  keyIsDown: KeyDownState;
  menuItemIsOpenByKey: MenuItemsOpenState;
  menuItemToggled: MenuItemToggled;
  showCopyDialog: (docRefUuids: string[], destination: DocRefType) => void;
  showMoveDialog: (docRefUuids: string[], destination: DocRefType) => void;
}

interface EnhancedProps extends Props, DragCollectedProps, DropCollectedProps {}

const dropTarget: DropTargetSpec<Props> = {
  canDrop({ menuItem: { docRef } }, monitor) {
    const { docRefs } = monitor.getItem();

    return (
      !!docRef &&
      docRefs.reduce(
        (acc: boolean, curr: DocRefType) => acc && canMove(curr, docRef),
        true,
      )
    );
  },
  drop({ menuItem: { docRef }, showCopyDialog, showMoveDialog }, monitor) {
    const { docRefs, isCopy } = monitor.getItem();
    const docRefUuids = docRefs.map((d: DocRefType) => d.uuid);

    if (docRef) {
      if (isCopy) {
        showCopyDialog(docRefUuids, docRef);
      } else {
        showMoveDialog(docRefUuids, docRef);
      }
    }
  },
};

const dropCollect: DropTargetCollector<
  DropCollectedProps,
  Props
> = function dropCollect(connect, monitor) {
  return {
    connectDropTarget: connect.dropTarget(),
    isOver: monitor.isOver(),
    canDrop: monitor.canDrop(),
  };
};

const dragSource: DragSourceSpec<Props, DragObject> = {
  canDrag({ menuItem: { docRef } }) {
    return !!docRef;
  },
  beginDrag({ menuItem: { docRef }, keyIsDown: { Control, Meta } }) {
    return {
      docRefs: docRef ? [docRef] : [],
      isCopy: !!(Control || Meta),
    };
  },
};

const dragCollect: DragSourceCollector<
  DragCollectedProps,
  Props
> = function dragCollect(connect, monitor) {
  return {
    connectDragSource: connect.dragSource(),
    isDragging: monitor.isDragging(),
  };
};

const MenuItem: React.FunctionComponent<EnhancedProps> = ({
  menuItem,
  isOver,
  canDrop,
  className: rawClassName,
  menuItemIsOpenByKey,
  depth,
  connectDropTarget,
  connectDragSource,
  isCollapsed,
  selectedItems,
  menuItemToggled,
  highlightedItem,
  activeMenuItem,
}) => {
  const isSelected: boolean = React.useMemo(
    () => selectedItems.includes(menuItem.key),
    [menuItem, selectedItems],
  );
  const hasHighlight: boolean = React.useMemo(
    () => !!highlightedItem && highlightedItem.key === menuItem.key,
    [menuItem, highlightedItem],
  );

  const onExpand: React.MouseEventHandler<HTMLDivElement> = React.useCallback(
    (e: React.MouseEvent) => {
      menuItemToggled(menuItem.key);
      e.preventDefault();
      e.stopPropagation(); // prevent onSelect from also firing
    },
    [menuItem.key, menuItemToggled],
  );
  const onSelect: React.MouseEventHandler<HTMLDivElement> = React.useCallback(
    (e: React.MouseEvent) => {
      menuItem.onClick();
      e.preventDefault();
    },
    [menuItem],
  );

  const isShowingChildren: boolean = menuItemIsOpenByKey[menuItem.key];
  const hasChildren: boolean =
    menuItem.children && menuItem.children.length > 0;

  const className = React.useMemo(() => {
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
    if (hasHighlight) {
      classNames.push("highlighted-item");
    }
    if (isSelected) {
      classNames.push("selected-item");
    }

    if (hasChildren && isShowingChildren) {
      classNames.push("has-children--open");
    }

    if (menuItem.key === activeMenuItem) {
      classNames.push("is-active");
    }

    return classNames.join(" ");
  }, [
    menuItem,
    activeMenuItem,
    isOver,
    canDrop,
    rawClassName,
    isShowingChildren,
    hasChildren,
    hasHighlight,
    isSelected,
  ]);

  const style = { paddingLeft: `${depth * 1.5}rem` };

  const hasChildrenIcon: IconProp = React.useMemo(
    () => (isShowingChildren ? "folder-open" : "folder-plus"),
    [isShowingChildren],
  );

  return connectDragSource(
    connectDropTarget(
      <div onClick={onSelect} className={className} style={style}>
        {hasChildren ? (
          <div className="menu-item__menu-icon" onClick={onExpand}>
            <FontAwesomeIcon size="lg" icon={hasChildrenIcon} />
          </div>
        ) : (
          <div className="menu-item__menu-icon">
            <FontAwesomeIcon size="lg" icon={menuItem.icon} />
          </div>
        )}
        {isCollapsed ? undefined : (
          <span onClick={onSelect} className="menu-item__text">
            {menuItem.title}
          </span>
        )}
      </div>,
    ),
  );
};

const enhance = (d: React.FunctionComponent<EnhancedProps>) =>
  DropTarget<Props>(
    [DragDropTypes.DOC_REF_UUIDS],
    dropTarget,
    dropCollect,
  )(DragSource<Props>(DragDropTypes.DOC_REF_UUIDS, dragSource, dragCollect)(d));

// dnd_error: temporarily disable dnd-related code to get the build working
export default enhance;
//const EnhancedMenuItem: React.FunctionComponent<Props> = enhance(MenuItem);

//export default EnhancedMenuItem;
