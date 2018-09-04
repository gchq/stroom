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
import React from 'react';
import PropTypes, { object } from 'prop-types';

import { connect } from 'react-redux';
import { compose, lifecycle, withProps, withHandlers } from 'recompose';
import { withRouter } from 'react-router-dom';
import { Button } from 'semantic-ui-react/dist/commonjs';
import Mousetrap from 'mousetrap';

import { actionCreators as selectableItemListingActionCreators } from 'lib/withSelectableItemListing';
import { actionCreators as appChromeActionCreators } from './redux';
import withLocalStorage from 'lib/withLocalStorage';
import MenuItem from './MenuItem';
import {
  MoveDocRefDialog,
  RenameDocRefDialog,
  CopyDocRefDialog,
  DeleteDocRefDialog,
  withDocumentTree,
} from 'components/FolderExplorer';

import { actionCreators as userSettingsActionCreators } from 'sections/UserSettings';
import withSelectableItemListing from 'lib/withSelectableItemListing';

const { selectionToggled } = selectableItemListingActionCreators;
const { menuItemOpened } = appChromeActionCreators;
const { themeChanged } = userSettingsActionCreators;

const withIsExpanded = withLocalStorage('isExpanded', 'setIsExpanded', true);

const LISTING_ID = 'app-chrome-menu';
const pathPrefix = '/s';

const getDocumentTreeMenuItems = (openDocRef, parentDocRef, treeNode, skipInContractedMenu = false) => ({
  key: treeNode.uuid,
  title: treeNode.name,
  onClick: () => openDocRef(treeNode),
  icon: 'folder',
  style: skipInContractedMenu ? 'doc' : 'nav',
  skipInContractedMenu,
  docRef: treeNode,
  parentDocRef,
  children:
    treeNode.children &&
    treeNode.children.length > 0 &&
    treeNode.children
      .filter(t => t.type === 'Folder')
      .map(t => getDocumentTreeMenuItems(openDocRef, treeNode, t, true)),
});

const getOpenMenuItems = (menuItems, areMenuItemsOpen, openMenuItems = []) => {
  menuItems.forEach((menuItem) => {
    openMenuItems.push(menuItem);
    if (menuItem.children && areMenuItemsOpen[menuItem.key]) {
      getOpenMenuItems(menuItem.children, areMenuItemsOpen, openMenuItems);
    }
  });

  return openMenuItems;
};

const enhance = compose(
  withDocumentTree,
  withRouter,
  withHandlers({
    openDocRef: ({ history }) => d => history.push(`/s/doc/${d.type}/${d.uuid}`),
  }),
  connect(
    (
      {
        selectableItemListings,
        userSettings: { theme },
        folderExplorer: { documentTree },
        appChrome: { areMenuItemsOpen },
      },
      props,
    ) => ({
      documentTree,
      areMenuItemsOpen,
      theme,
      selectableItemListing: selectableItemListings[LISTING_ID],
    }),
    {
      menuItemOpened,
      themeChanged,
      selectionToggled
    },
  ),
  withIsExpanded,
  // We need to work out how to do these global shortcuts from scratch, now that we don't have dedicated pages
  // lifecycle({
  //   componentDidMount() {
  //     Mousetrap.bind('ctrl+shift+e', () => this.props.history.push('/s/recentItems'));
  //     Mousetrap.bind('ctrl+shift+f', () => this.props.history.push('/s/search'));
  //   },
  // }),
  withProps(({
    history, openDocRef, documentTree, areMenuItemsOpen, menuItemOpened,
  }) => {
    const menuItems = [
      {
        key: 'welcome',
        title: 'Welcome',
        onClick: () => history.push(`${pathPrefix}/welcome/`),
        icon: 'home',
        style: 'nav',
      },
      getDocumentTreeMenuItems(openDocRef, null, documentTree),
      {
        key: 'data',
        title: 'Data',
        onClick: () => history.push(`${pathPrefix}/data`),
        icon: 'database',
        style: 'nav',
      },
      {
        key: 'processing',
        title: 'Processing',
        onClick: () => history.push(`${pathPrefix}/processing`),
        icon: 'play',
        style: 'nav',
      },
      {
        key: 'admin',
        title: 'Admin',
        onClick: () => menuItemOpened('admin', !areMenuItemsOpen.admin),
        icon: 'cogs',
        style: 'nav',
        skipInContractedMenu: true,
        children: [
          {
            key: 'admin-me',
            title: 'Me',
            onClick: () => history.push(`${pathPrefix}/me`),
            icon: 'user',
            style: 'nav',
          },
          {
            key: 'admin-users',
            title: 'Users',
            onClick: () => history.push(`${pathPrefix}/users`),
            icon: 'users',
            style: 'nav',
          },
          {
            key: 'admin-apikeys',
            title: 'API Keys',
            onClick: () => history.push(`${pathPrefix}/apikeys`),
            icon: 'key',
            style: 'nav',
          },
        ],
      },
    ];
    const openMenuItems = getOpenMenuItems(menuItems, areMenuItemsOpen);

    return {
      menuItems,
      openMenuItems,
    };
  }),
  withSelectableItemListing(({ openMenuItems, menuItemOpened, areMenuItemsOpen, selectionToggled }) => ({
    listingId: LISTING_ID,
    items: openMenuItems,
    openItem: m => m.onClick(),
    enterItem: m => menuItemOpened(m.key, true),
    goBack: m => {
      if (m) {
        if (areMenuItemsOpen[m.key]) {
          menuItemOpened(m.key, false);
        } else if (m.parentDocRef) {
          // Can we bubble back up to the parent folder of the current selection?
          let newSelection = openMenuItems.findIndex(({key}) => key === m.parentDocRef.uuid);
          selectionToggled(LISTING_ID, newSelection);
          menuItemOpened(m.parentDocRef.uuid, false);
        }
      }
    },
  })),
);

const getExpandedMenuItems = (menuItems, areMenuItemsOpen, depth = 0) =>
  menuItems.map(menuItem => (
    <React.Fragment key={menuItem.key}>
      <MenuItem
        className="sidebar__text-color"
        key={menuItem.key}
        menuItem={menuItem}
        depth={depth}
        listingId={LISTING_ID}
      />
      {menuItem.children &&
        areMenuItemsOpen[menuItem.key] &&
        getExpandedMenuItems(menuItem.children, areMenuItemsOpen, depth + 1)}
    </React.Fragment>
  ));

const getContractedMenuItems = menuItems =>
  menuItems.map(menuItem => (
    <React.Fragment key={menuItem.key}>
      {!menuItem.skipInContractedMenu && ( // just put the children of menu items into the sidebar
        <Button
          className="app-chrome__sidebar__toggle_collapsed raised-high borderless app-chrome__sidebar__toggle"
          key={menuItem.title}
          icon={menuItem.icon}
          onClick={menuItem.onClick}
        />
      )}
      {menuItem.children && getContractedMenuItems(menuItem.children)}
    </React.Fragment>
  ));

const AppChrome = ({
  headerContent,
  icon,
  content,
  isExpanded,
  menuItems,
  areMenuItemsOpen,
  menuItemOpened,
  setIsExpanded,
  theme,
  themeChanged,
  onKeyDownWithShortcuts,
}) => {
  if (theme === undefined) {
    theme = 'theme-dark';
    themeChanged(theme);
  }
  return (
    <div className={`app-container ${theme}`}>
      <div className="app-chrome flat">
        <MoveDocRefDialog />
        <RenameDocRefDialog />
        <DeleteDocRefDialog />
        <CopyDocRefDialog />
        <div className="raised-high">
          {isExpanded ? (
            <React.Fragment>
              <div className="app-chrome__sidebar_header header">
                <Button
                  aria-label="Show/hide the sidebar"
                  size="large"
                  className="app-chrome__sidebar__toggle raised-high borderless "
                  icon="bars"
                  onClick={() => setIsExpanded(!isExpanded)}
                />
                <img
                  className="sidebar__logo"
                  alt="Stroom logo"
                  src={require('../../images/logo.svg')}
                />
              </div>
              <div
                tabIndex={0}
                onKeyDown={onKeyDownWithShortcuts}
                className="app-chrome__sidebar-menu raised-high"
              >
                {getExpandedMenuItems(menuItems, areMenuItemsOpen)}
              </div>
            </React.Fragment>
          ) : (
            <Button.Group vertical className="app-chrome__sidebar__buttons">
              <Button
                size="large"
                icon="bars"
                className="app-chrome__sidebar__toggle_collapsed raised-high borderless app-chrome__sidebar__toggle"
                onClick={() => setIsExpanded(!isExpanded)}
              />
              {getContractedMenuItems(menuItems)}
            </Button.Group>
          )}
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

AppChrome.contextTypes = {
  store: PropTypes.object,
  router: PropTypes.shape({
    history: object.isRequired,
  }),
};

AppChrome.propTypes = {
  activeMenuItem: PropTypes.string.isRequired,
  content: PropTypes.object.isRequired,
};

export default enhance(AppChrome);
