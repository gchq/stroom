import * as React from "react";

import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import useLocalStorage, { storeBoolean } from "lib/useLocalStorage";
import useMenuItems from "./useMenuItems";
import { ShowDialog as ShowCopyDocRefDialog } from "components/DocumentEditors/FolderExplorer/CopyMoveDocRefDialog/types";
import {
  CopyMoveDocRefDialog,
  useDialog as useCopyMoveDocRefDialog,
} from "components/DocumentEditors/FolderExplorer/CopyMoveDocRefDialog";
import useKeyIsDown, { KeyDownState } from "lib/useKeyIsDown";
import useSelectableItemListing from "lib/useSelectableItemListing";
import {
  MenuItemsOpenState,
  MenuItemType,
  MenuItemToggled,
} from "./MenuItem/types";
/* import MenuItem from "./MenuItem"; */
import { useDocumentTree } from "components/DocumentEditors/api/explorer";
import { ActiveMenuItem } from "../types";
import ActivitySummary from "components/Activity/ActivitySummary";

interface Props {
  activeMenuItem: ActiveMenuItem;
}

const getMenuItems = (
  activeMenuItem: string,
  isCollapsed = false,
  menuItems: MenuItemType[],
  menuItemIsOpenByKey: MenuItemsOpenState,
  menuItemToggled: MenuItemToggled,
  keyIsDown: KeyDownState,
  showCopyDialog: ShowCopyDocRefDialog,
  showMoveDialog: ShowCopyDocRefDialog,
  selectedItems: string[],
  highlightedItem?: MenuItemType,
  depth = 0,
) =>
  menuItems.map((menuItem) => (
    <React.Fragment key={menuItem.key}>
      {/* dnd_error: temporarily disable dnd-related code to get the build working */}
      {/*      <MenuItem
        keyIsDown={keyIsDown}
        selectedItems={selectedItems}
        highlightedItem={highlightedItem}
        className={`sidebar__text-color ${isCollapsed ? "collapsed" : ""} ${
          depth > 0 ? "child" : ""
        }`}
        key={menuItem.key}
        activeMenuItem={activeMenuItem}
        menuItem={menuItem}
        depth={depth}
        isCollapsed={isCollapsed}
        showCopyDialog={showCopyDialog}
        showMoveDialog={showMoveDialog}
        menuItemToggled={menuItemToggled}
        menuItemIsOpenByKey={menuItemIsOpenByKey}
      />*/}
      {/* TODO: we only want the 'children' class on the first set of children. We're using it to pad the bottom. Any better ideas? */}
      {menuItem.children && menuItemIsOpenByKey[menuItem.key] ? (
        <div className={`${depth === 0 ? "sidebar__children" : ""}`}>
          {getMenuItems(
            activeMenuItem,
            isCollapsed,
            menuItem.children,
            menuItemIsOpenByKey,
            menuItemToggled,
            keyIsDown,
            showCopyDialog,
            showMoveDialog,
            selectedItems,
            highlightedItem,
            depth + 1,
          )}
        </div>
      ) : undefined}
    </React.Fragment>
  ));

const Sidebar: React.FunctionComponent<Props> = ({ activeMenuItem }) => {
  const {
    menuItems,
    openMenuItemKeys,
    menuItemOpened,
    menuItemToggled,
    menuItemIsOpenByKey,
    menuItemsByKey,
  } = useMenuItems();

  const { copyDocuments, moveDocuments } = useDocumentTree();

  const { value: isExpanded, reduceValue: setIsExpanded } = useLocalStorage(
    "isExpanded",
    true,
    storeBoolean,
  );
  const toggleIsExpanded = React.useCallback(
    () => setIsExpanded((existingIsExpanded) => !existingIsExpanded),
    [setIsExpanded],
  );
  const sidebarClassName = isExpanded
    ? "app-chrome__sidebar--expanded"
    : "app-chrome__sidebar--collapsed";

  const getKey = React.useCallback((key: string) => key, []);
  const openItem = React.useCallback(
    (key: string) => {
      const menuItem = menuItemsByKey[key];
      // call the onclick
      menuItem.onClick();
    },
    [menuItemsByKey],
  );
  const enterItem = React.useCallback((m) => menuItemOpened(m, true), [
    menuItemOpened,
  ]);
  const goBack = React.useCallback(
    (key: string) => {
      const menuItem = menuItemsByKey[key];
      if (menuItemIsOpenByKey[key]) {
        menuItemOpened(key, false);
      } else if (!!menuItem.parentDocRef) {
        // Can we bubble back up to the parent folder of the current selection?
        // let newSelection = openMenuItems.find(
        //   ({ key }: MenuItemType) =>
        //     !!m.parentDocRef && key === m.parentDocRef.uuid,
        // );
        // if (!!newSelection) {
        //   toggleSelection(newSelection.key);
        // }
        menuItemOpened(menuItem.parentDocRef.uuid, false);
      }
    },
    [menuItemIsOpenByKey, menuItemOpened, menuItemsByKey],
  );

  const keyIsDown = useKeyIsDown();
  const {
    onKeyDown,
    selectedItems,
    highlightedItem,
  } = useSelectableItemListing<string>({
    items: openMenuItemKeys,
    getKey,
    openItem,
    enterItem,
    goBack,
  });

  const {
    showDialog: showCopyDialog,
    componentProps: copyDialogComponentProps,
  } = useCopyMoveDocRefDialog(copyDocuments);
  const {
    showDialog: showMoveDialog,
    componentProps: moveDialogComponentProps,
  } = useCopyMoveDocRefDialog(moveDocuments);

  return (
    <div className={`app-chrome__sidebar ${sidebarClassName}`}>
      <CopyMoveDocRefDialog {...copyDialogComponentProps} />
      <CopyMoveDocRefDialog {...moveDialogComponentProps} />
      <React.Fragment>
        <div className="app-chrome__sidebar_header">
          {isExpanded ? (
            <img
              className="sidebar__logo"
              alt="Stroom logo"
              src={require("../../../images/logo.svg")}
            />
          ) : undefined}
          <div
            className="app-chrome__sidebar_header_icon"
            onClick={toggleIsExpanded}
          >
            <FontAwesomeIcon
              aria-label="Show/hide the sidebar"
              className="menu-item__menu-icon sidebar__toggle sidebar__menu-item borderless "
              icon="bars"
              size="2x"
            />
          </div>
        </div>
        <div
          tabIndex={0}
          onKeyDown={onKeyDown}
          className="app-chrome__sidebar-menu"
          data-simplebar
        >
          <div className="app-chrome__sidebar-menu__container">
            {getMenuItems(
              activeMenuItem,
              !isExpanded,
              menuItems,
              menuItemIsOpenByKey,
              menuItemToggled,
              keyIsDown,
              showCopyDialog,
              showMoveDialog,
              selectedItems,
              highlightedItem,
            )}
          </div>

          <div className="app-chrome__sidebar-menu__activity-summary">
            <ActivitySummary />
          </div>
        </div>
      </React.Fragment>
    </div>
  );
};

export default Sidebar;
