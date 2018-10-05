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

import Button from "../../components/Button";
import {
  actionCreators as selectableItemListingActionCreators,
  EnhancedProps as WithSelectableItemListingProps,
  StoreStatePerId as WithSelectableItemListingStatePerId
} from "../../lib/withSelectableItemListing";
import { actionCreators as appChromeActionCreators } from "./redux";
import { StoreState as MenuItemsOpenStoreState } from "./redux/menuItemsOpenReducer";
import withLocalStorage from "../../lib/withLocalStorage";
import MenuItem, { MenuItemType } from "./MenuItem";
import {
  MoveDocRefDialog,
  RenameDocRefDialog,
  CopyDocRefDialog,
  DeleteDocRefDialog,
  NewDocRefDialog,
  withDocumentTree,
  WithDocumentTreeProps
} from "../../components/FolderExplorer";

import { actionCreators as userSettingsActionCreators } from "../UserSettings";
import withSelectableItemListing from "../../lib/withSelectableItemListing";
import { DocRefType, DocRefConsumer, DocRefTree } from "../../types";
import { GlobalStoreState } from "../../startup/reducers";

const { selectionToggled } = selectableItemListingActionCreators;
const { menuItemOpened } = appChromeActionCreators;
const { themeChanged } = userSettingsActionCreators;

const withIsExpanded = withLocalStorage("isExpanded", "setIsExpanded", true);

const LISTING_ID = "app-chrome-menu";
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
) {
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
  theme: string;
  selectableItemListing: WithSelectableItemListingStatePerId;
}
interface ConnectDispatch {
  menuItemOpened: typeof menuItemOpened;
  themeChanged: typeof themeChanged;
  selectionToggled: typeof selectionToggled;
}
interface WithIsExpanded {
  isExpanded: boolean;
  setIsExpanded: (value: boolean) => any;
}
interface WithProps {
  menuItems: Array<MenuItemType>;
  openMenuItems: { [key: string]: boolean };
}

interface EnhancedProps
  extends Props,
    WithDocumentTreeProps,
    RouteComponentProps<any>,
    WithHandlers,
    ConnectState,
    ConnectDispatch,
    WithIsExpanded,
    WithProps,
    WithSelectableItemListingProps<MenuItemType> {}

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
    ({
      selectableItemListings,
      userSettings: { theme },
      appChrome: { areMenuItemsOpen }
    }) => ({
      areMenuItemsOpen,
      theme,
      selectableItemListing: selectableItemListings[LISTING_ID]
    }),
    {
      menuItemOpened,
      themeChanged,
      selectionToggled
    }
  ),
  withIsExpanded,
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
      menuItemOpened
    }) => {
      const menuItems: Array<MenuItemType> = [
        {
          key: "welcome",
          title: "Welcome",
          onClick: () => history.push(`${pathPrefix}/welcome/`),
          icon: "home",
          style: "nav"
        },
        getDocumentTreeMenuItems(openDocRef, undefined, documentTree),
        {
          key: "data",
          title: "Data",
          onClick: () => history.push(`${pathPrefix}/data`),
          icon: "database",
          style: "nav"
        },
        {
          key: "processing",
          title: "Processing",
          onClick: () => history.push(`${pathPrefix}/processing`),
          icon: "play",
          style: "nav"
        },
        {
          key: "admin",
          title: "Admin",
          onClick: () => menuItemOpened("admin", !areMenuItemsOpen.admin),
          icon: "cogs",
          style: "nav",
          skipInContractedMenu: true,
          children: [
            {
              key: "admin-me",
              title: "Me",
              onClick: () => history.push(`${pathPrefix}/me`),
              icon: "user",
              style: "nav"
            },
            {
              key: "admin-users",
              title: "Users",
              onClick: () => history.push(`${pathPrefix}/users`),
              icon: "users",
              style: "nav"
            },
            {
              key: "admin-apikeys",
              title: "API Keys",
              onClick: () => history.push(`${pathPrefix}/apikeys`),
              icon: "key",
              style: "nav"
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
  ),
  withSelectableItemListing<MenuItemType>(
    ({
      openMenuItems,
      menuItemOpened,
      areMenuItemsOpen,
      selectionToggled
    }) => ({
      listingId: LISTING_ID,
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
            selectionToggled(LISTING_ID, newSelection.key);
            menuItemOpened(m.parentDocRef.uuid, false);
          }
        }
      }
    })
  )
);

const getMenuItems = (
  isCollapsed: boolean = false,
  menuItems: Array<MenuItemType>,
  areMenuItemsOpen: MenuItemsOpenStoreState,
  depth: number = 0
) =>
  menuItems.map(menuItem => (
    <React.Fragment key={menuItem.key}>
      <MenuItem
        className={`sidebar__text-color ${isCollapsed ? "collapsed" : ""} ${
          depth > 0 ? "child" : ""
        }`}
        key={menuItem.key}
        menuItem={menuItem}
        depth={depth}
        listingId={LISTING_ID}
        isCollapsed={isCollapsed}
      />
      {menuItem.children &&
        areMenuItemsOpen[menuItem.key] &&
        getMenuItems(
          isCollapsed,
          menuItem.children,
          areMenuItemsOpen,
          depth + 1
        )}
    </React.Fragment>
  ));

const AppChrome = ({
  content,
  isExpanded,
  menuItems,
  areMenuItemsOpen,
  setIsExpanded,
  theme,
  themeChanged,
  onKeyDownWithShortcuts
}: EnhancedProps) => {
  if (theme === undefined) {
    theme = "theme-dark";
    themeChanged(theme);
  }

  const sidebarClassName = isExpanded
    ? "app-chrome__sidebar--expanded"
    : "app-chrome__sidebar--collapsed";
  return (
    <div className={`app-container ${theme}`}>
      <div className="app-chrome flat">
        <NewDocRefDialog listingId={LISTING_ID} />
        <MoveDocRefDialog listingId={LISTING_ID} />
        <RenameDocRefDialog listingId={LISTING_ID} />
        <DeleteDocRefDialog listingId={LISTING_ID} />
        <CopyDocRefDialog listingId={LISTING_ID} />
        <div className={`app-chrome__sidebar raised-high ${sidebarClassName}`}>
          <React.Fragment>
            <div className="app-chrome__sidebar_header header">
              <Button
                aria-label="Show/hide the sidebar"
                className="app-chrome__sidebar__toggle raised-high borderless "
                icon="bars"
                size="xlarge"
                onClick={() => setIsExpanded(!isExpanded)}
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
                {getMenuItems(!isExpanded, menuItems, areMenuItemsOpen)}
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
