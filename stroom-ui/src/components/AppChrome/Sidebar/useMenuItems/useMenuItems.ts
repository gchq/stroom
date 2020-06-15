import * as React from "react";

import useAppNavigation from "lib/useAppNavigation";
import useLocalStorage, { useStoreObjectFactory } from "lib/useLocalStorage";
import {
  MenuItemsOpenState,
  MenuItemOpened,
  MenuItemToggled,
  MenuItemType,
} from "../MenuItem/types";
import useDocumentMenu from "./useDocumentMenu";
import useAdminMenu from "./useAdminMenu";
import useWelcomeMenu from "./useWelcomeMenu";
import useIndexingMenu from "./useIndexingMenu";
import { SubMenuProps } from "./types";
import useDataViewerMenu from "./useDataViewerMenu";
import useProcessingMenu from "./useProcessingMenu";

interface MenuItemsByKey {
  [key: string]: MenuItemType;
}

interface OutProps {
  menuItems: MenuItemType[];
  menuItemsByKey: MenuItemsByKey;
  menuItemIsOpenByKey: MenuItemsOpenState;
  openMenuItemKeys: string[];
  menuItemToggled: MenuItemToggled;
  menuItemOpened: MenuItemOpened;
}

const iterateMenuItems = function (
  menuItems: MenuItemType[],
  callback: (menuItem: MenuItemType) => void,
) {
  menuItems.forEach((menuItem) => {
    callback(menuItem);
    if (!!menuItem.children) {
      iterateMenuItems(menuItem.children, callback);
    }
  });
};

const DEFAULT_MENU_OPEN_STATE: MenuItemsOpenState = {};

const useMenuItems = (): OutProps => {
  const navigateApp = useAppNavigation();

  const {
    value: menuItemIsOpenByKey,
    reduceValue: modifyOpenMenuItems,
  } = useLocalStorage<MenuItemsOpenState>(
    "app-chrome-menu-items-open",
    DEFAULT_MENU_OPEN_STATE,
    useStoreObjectFactory<MenuItemsOpenState>(),
  );
  const menuItemOpened: MenuItemOpened = React.useCallback(
    (name: string, isOpen: boolean) => {
      modifyOpenMenuItems((existing) => ({
        ...existing,
        [name]: isOpen,
      }));
    },
    [modifyOpenMenuItems],
  );

  const menuItemToggled: MenuItemToggled = React.useCallback(
    (name: string) => {
      modifyOpenMenuItems((existing) => ({
        ...existing,
        [name]: !existing[name],
      }));
    },
    [modifyOpenMenuItems],
  );
  const subMenuProps: SubMenuProps = { navigateApp, menuItemToggled };

  const welcome: MenuItemType = useWelcomeMenu(subMenuProps);
  const documentMenuItems = useDocumentMenu(subMenuProps);
  const dataViewer: MenuItemType = useDataViewerMenu(subMenuProps);
  const processing: MenuItemType = useProcessingMenu(subMenuProps);
  const indexing: MenuItemType = useIndexingMenu(subMenuProps);
  const admin: MenuItemType = useAdminMenu(subMenuProps);

  const menuItems: MenuItemType[] = React.useMemo(
    () => [welcome, documentMenuItems, dataViewer, processing, indexing, admin],
    [documentMenuItems, welcome, dataViewer, processing, indexing, admin],
  );

  const openMenuItemKeys: string[] = React.useMemo(
    () =>
      Object.entries(menuItemIsOpenByKey)
        .filter((k) => k[1])
        .map((k) => k[0]),
    [menuItemIsOpenByKey],
  );

  const menuItemsByKey: MenuItemsByKey = React.useMemo(() => {
    const itemsByKey = {};

    iterateMenuItems(
      menuItems,
      (menuItem) => (itemsByKey[menuItem.key] = menuItem),
    );

    return itemsByKey;
  }, [menuItems]);

  return {
    menuItems,
    openMenuItemKeys,
    menuItemOpened,
    menuItemToggled,
    menuItemIsOpenByKey,
    menuItemsByKey,
  };
};

export default useMenuItems;
