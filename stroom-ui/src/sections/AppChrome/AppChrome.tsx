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
import * as React from "react";

import { connect } from "react-redux";
import { compose, withProps, withHandlers } from "recompose";
import { withRouter, RouteComponentProps } from "react-router-dom";

import "simplebar";
import "simplebar/dist/simplebar.css";

import { actionCreators as appChromeActionCreators } from "./redux";
import { StoreState as MenuItemsOpenStoreState } from "./redux/menuItemsOpenReducer";
import MenuItem, { MenuItemType } from "./MenuItem";
import {
  withDocumentTree,
  WithDocumentTreeProps
} from "../../components/FolderExplorer";

import {
  copyDocuments,
  moveDocuments
} from "../../components/FolderExplorer/explorerClient";
import useSelectableItemListing from "../../lib/useSelectableItemListing";
import { DocRefType, DocRefConsumer, DocRefTree } from "../../types";
import { GlobalStoreState } from "../../startup/reducers";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { KeyDownState } from "../../lib/useKeyIsDown/useKeyIsDown";
import CopyMoveDocRefDialog, {
  useCopyMoveDocRefDialog,
  ShowCopyDocRefDialog
} from "../../components/FolderExplorer/CopyMoveDocRefDialog";
import useLocalStorage from "../../lib/useLocalStorage";
import { useTheme } from "../../lib/theme";

const { menuItemOpened } = appChromeActionCreators;

const pathPrefix = "/s";

const getDocumentTreeMenuItems = (
  openDocRef: DocRefConsumer,
  parentDocRef: DocRefType | undefined,
  treeNode: DocRefTree,
  skipInContractedMenu = false
): MenuItemType => ({
  key: treeNode.uuid,
  title: treeNode.name,
  onClick: () => openDocRef(treeNode),
  icon: "folder",
  style: skipInContractedMenu ? "doc" : "nav",
  skipInContractedMenu,
  docRef: treeNode,
  parentDocRef,
  children:
    treeNode.children && treeNode.children.length > 0
      ? treeNode.children
          .filter(t => t.type === "Folder")
          .map(t => getDocumentTreeMenuItems(openDocRef, treeNode, t, true))
      : undefined
});

const getOpenMenuItems = function<
  T extends {
    key: string;
    children?: Array<T>;
  }
>(
  menuItems: Array<T>,
  areMenuItemsOpen: MenuItemsOpenStoreState,
  openMenuItems: Array<T> = []
): Array<T> {
  menuItems.forEach(menuItem => {
    openMenuItems.push(menuItem);
    if (menuItem.children && areMenuItemsOpen[menuItem.key]) {
      getOpenMenuItems(menuItem.children, areMenuItemsOpen, openMenuItems);
    }
  });

  return openMenuItems;
};

interface Props {
  content: React.ReactNode;
  activeMenuItem: string;
}
interface WithHandlers {
  openDocRef: DocRefConsumer;
}
interface ConnectState {
  areMenuItemsOpen: MenuItemsOpenStoreState;
}
interface ConnectDispatch {
  menuItemOpened: typeof menuItemOpened;
  copyDocuments: typeof copyDocuments;
  moveDocuments: typeof moveDocuments;
}
interface WithIsExpanded {
  isExpanded: boolean;
  setIsExpanded: (value: boolean) => any;
}
interface WithProps {
  menuItems: Array<MenuItemType>;
  openMenuItems: Array<MenuItemType>;
}

interface EnhancedProps
  extends Props,
    WithDocumentTreeProps,
    RouteComponentProps<any>,
    WithHandlers,
    ConnectState,
    ConnectDispatch,
    WithIsExpanded,
    WithProps {}

const enhance = compose<EnhancedProps, Props>(
  withDocumentTree,
  withRouter,
  withHandlers({
    openDocRef: ({ history }) => (d: DocRefType) =>
      history.push(`/s/doc/${d.type}/${d.uuid}`)
  }),
  connect<
    ConnectState,
    ConnectDispatch,
    Props & WithDocumentTreeProps & RouteComponentProps<any> & WithHandlers,
    GlobalStoreState
  >(
    ({ routing: { location }, appChrome: { areMenuItemsOpen } }) => ({
      areMenuItemsOpen,
      location
    }),
    {
      menuItemOpened,
      copyDocuments,
      moveDocuments
    }
  ),
  // We need to work out how to do these global shortcuts from scratch, now that we don't have dedicated pages
  // lifecycle({
  //   componentDidMount() {
  //     Mousetrap.bind('ctrl+shift+e', () => this.props.history.push('/s/recentItems'));
  //     Mousetrap.bind('ctrl+shift+f', () => this.props.history.push('/s/search'));
  //   },
  // }),
  withProps(
    ({
      history,
      openDocRef,
      documentTree,
      areMenuItemsOpen,
      menuItemOpened,
      location
    }) => {
      const menuItems: Array<MenuItemType> = [
        {
          key: "welcome",
          title: "Welcome",
          onClick: () => history.push(`${pathPrefix}/welcome/`),
          icon: "home",
          style: "nav",
          isActive:
            location && location.pathname.includes(`${pathPrefix}/welcome/`)
        },
        getDocumentTreeMenuItems(openDocRef, undefined, documentTree),
        {
          key: "data",
          title: "Data",
          onClick: () => history.push(`${pathPrefix}/data`),
          icon: "database",
          style: "nav",
          isActive: location && location.pathname.includes(`${pathPrefix}/data`)
        },
        {
          key: "processing",
          title: "Processing",
          onClick: () => history.push(`${pathPrefix}/processing`),
          icon: "play",
          style: "nav",
          isActive:
            location && location.pathname.includes(`${pathPrefix}/processing`)
        },
        {
          key: "indexing",
          title: "Indexing",
          onClick: () => menuItemOpened("indexing", !areMenuItemsOpen.indexing),
          icon: "database",
          style: "nav",
          skipInContractedMenu: true,
          isActive:
            location &&
            (location.pathname.includes(`${pathPrefix}/indexing/volumes`) ||
              location.pathname.includes(`${pathPrefix}/indexing/groups`)),
          children: [
            {
              key: "indexing-volumes",
              title: "Index Volumes",
              onClick: () => history.push(`${pathPrefix}/indexing/volumes`),
              icon: "database",
              style: "nav",
              isActive:
                location &&
                location.pathname.includes(`${pathPrefix}/indexing/volumes`)
            },
            {
              key: "indexing-groups",
              title: "Index Groups",
              onClick: () => history.push(`${pathPrefix}/indexing/groups`),
              icon: "database",
              style: "nav",
              isActive:
                location &&
                location.pathname.includes(`${pathPrefix}/indexing/groups`)
            }
          ]
        },
        {
          key: "admin",
          title: "Admin",
          onClick: () => menuItemOpened("admin", !areMenuItemsOpen.admin),
          icon: "cogs",
          style: "nav",
          skipInContractedMenu: true,
          isActive:
            location &&
            (location.pathname.includes("/s/me") ||
              location.pathname.includes("/s/users") ||
              location.pathname.includes("/s/apikeys")),
          children: [
            {
              key: "admin-me",
              title: "Me",
              onClick: () => history.push(`${pathPrefix}/me`),
              icon: "user",
              style: "nav",
              isActive: location && location.pathname.includes("/s/me")
            },
            {
              key: "admin-user-permissions",
              title: "User Permissions",
              onClick: () => history.push(`${pathPrefix}/userPermissions`),
              icon: "users",
              style: "nav",
              isActive:
                location && location.pathname.includes("/s/userPermissions")
            },
            {
              key: "admin-users",
              title: "Users",
              onClick: () => history.push(`${pathPrefix}/users`),
              icon: "users",
              style: "nav",
              isActive: location && location.pathname.includes("/s/users")
            },
            {
              key: "admin-apikeys",
              title: "API Keys",
              onClick: () => history.push(`${pathPrefix}/apikeys`),
              icon: "key",
              style: "nav",
              isActive: location && location.pathname.includes("/s/apikeys")
            }
          ]
        }
      ];
      const openMenuItems = getOpenMenuItems(menuItems, areMenuItemsOpen);

      return {
        menuItems,
        openMenuItems
      };
    }
  )
);

const getMenuItems = (
  isCollapsed: boolean = false,
  menuItems: Array<MenuItemType>,
  areMenuItemsOpen: MenuItemsOpenStoreState,
  keyIsDown: KeyDownState,
  showCopyDialog: ShowCopyDocRefDialog,
  showMoveDialog: ShowCopyDocRefDialog,
  selectedItems: Array<MenuItemType>,
  focussedItem?: MenuItemType,
  depth: number = 0
) =>
  menuItems.map(menuItem => (
    <React.Fragment key={menuItem.key}>
      <MenuItem
        keyIsDown={keyIsDown}
        selectedItems={selectedItems}
        focussedItem={focussedItem}
        className={`sidebar__text-color ${isCollapsed ? "collapsed" : ""} ${
          depth > 0 ? "child" : ""
        }`}
        key={menuItem.key}
        menuItem={menuItem}
        depth={depth}
        isCollapsed={isCollapsed}
        showCopyDialog={showCopyDialog}
        showMoveDialog={showMoveDialog}
      />
      {/* TODO: we only want the 'children' class on the first set of children. We're using it to pad the bottom. Any better ideas? */}
      {menuItem.children && areMenuItemsOpen[menuItem.key] ? (
        <div className={`${depth === 0 ? "sidebar__children" : ""}`}>
          {getMenuItems(
            isCollapsed,
            menuItem.children,
            areMenuItemsOpen,
            keyIsDown,
            showCopyDialog,
            showMoveDialog,
            selectedItems,
            focussedItem,
            depth + 1
          )}
        </div>
      ) : (
        undefined
      )}
    </React.Fragment>
  ));

const AppChrome = ({
  content,
  menuItems,
  areMenuItemsOpen,
  openMenuItems,
  menuItemOpened,
  copyDocuments,
  moveDocuments
}: EnhancedProps) => {
  const { theme } = useTheme();

  const { value: isExpanded, setValue: setIsExpanded } = useLocalStorage(
    "isExpanded",
    true
  );

  const {
    onKeyDownWithShortcuts,
    selectionToggled,
    selectedItems,
    focussedItem,
    keyIsDown
  } = useSelectableItemListing<MenuItemType>({
    items: openMenuItems,
    getKey: m => m.key,
    openItem: m => m.onClick(),
    enterItem: m => menuItemOpened(m.key, true),
    goBack: m => {
      if (m) {
        if (areMenuItemsOpen[m.key]) {
          menuItemOpened(m.key, false);
        } else if (m.parentDocRef) {
          // Can we bubble back up to the parent folder of the current selection?
          let newSelection = openMenuItems.find(
            ({ key }: MenuItemType) => key === m.parentDocRef!.uuid
          );
          if (!!newSelection) {
            selectionToggled(newSelection.key);
          }
          menuItemOpened(m.parentDocRef.uuid, false);
        }
      }
    }
  });

  const {
    showDialog: showCopyDialog,
    componentProps: copyDialogComponentProps
  } = useCopyMoveDocRefDialog(copyDocuments);
  const {
    showDialog: showMoveDialog,
    componentProps: moveDialogComponentProps
  } = useCopyMoveDocRefDialog(moveDocuments);

  const sidebarClassName = isExpanded
    ? "app-chrome__sidebar--expanded"
    : "app-chrome__sidebar--collapsed";
  return (
    <div className={`app-container ${theme}`}>
      <div className="app-chrome flat">
        <CopyMoveDocRefDialog {...copyDialogComponentProps} />
        <CopyMoveDocRefDialog {...moveDialogComponentProps} />
        <div className={`app-chrome__sidebar raised-high ${sidebarClassName}`}>
          <React.Fragment>
            <div
              className="app-chrome__sidebar_header header"
              onClick={() => setIsExpanded(!isExpanded)}
            >
              <FontAwesomeIcon
                aria-label="Show/hide the sidebar"
                className="menu-item__menu-icon sidebar__toggle sidebar__menu-item borderless "
                icon="bars"
                size="2x"
              />
              {isExpanded ? (
                <img
                  className="sidebar__logo"
                  alt="Stroom logo"
                  src={require("../../images/logo.svg")}
                />
              ) : (
                undefined
              )}
            </div>
            <div
              tabIndex={0}
              onKeyDown={onKeyDownWithShortcuts}
              className="app-chrome__sidebar-menu raised-high"
              data-simplebar
            >
              <div className="app-chrome__sidebar-menu__container">
                {getMenuItems(
                  !isExpanded,
                  menuItems,
                  areMenuItemsOpen,
                  keyIsDown,
                  showCopyDialog,
                  showMoveDialog,
                  selectedItems,
                  focussedItem
                )}
              </div>
            </div>
          </React.Fragment>
        </div>
        <div className="app-chrome__content">
          <div className="content-tabs">
            <div className="content-tabs__content">{content}</div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default enhance(AppChrome);
