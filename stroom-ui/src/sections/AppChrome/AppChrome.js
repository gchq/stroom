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
import { compose, lifecycle, withProps } from 'recompose';
import { withRouter } from 'react-router-dom';
import { Button, Icon } from 'semantic-ui-react';
import Mousetrap from 'mousetrap';

import { actionCreators as appChromeActionCreators } from './redux';
import { withExplorerTree } from 'components/DocExplorer';
import withLocalStorage from 'lib/withLocalStorage';
import { openDocRef } from 'prototypes/RecentItems';

const { menuItemOpened } = appChromeActionCreators;

const withIsExpanded = withLocalStorage('isExpanded', 'setIsExpanded', true);

const SIDE_BAR_COLOUR = 'blue';

const pathPrefix = '/s';

const getDocumentTreeMenuItems = (openDocRef, treeNode, skipInContractedMenu = false) => ({
  key: treeNode.uuid,
  title: treeNode.name,
  onClick: () => openDocRef(treeNode),
  icon: 'folder',
  style: skipInContractedMenu ? 'doc' : 'nav',
  skipInContractedMenu,
  children:
    treeNode.children &&
    treeNode.children.length > 0 &&
    treeNode.children
      .filter(t => t.type === 'Folder')
      .map(t => getDocumentTreeMenuItems(openDocRef, t, true)),
});

const enhance = compose(
  withExplorerTree,
  connect(
    (
      {
        docExplorer: {
          explorerTree: { documentTree },
        },
        appChrome: { menuItemsOpen },
      },
      props,
    ) => ({
      documentTree,
      menuItemsOpen,
    }),
    {
      menuItemOpened,
      openDocRef,
    },
  ),
  withRouter,
  withIsExpanded,
  lifecycle({
    componentDidMount() {
      Mousetrap.bind('ctrl+shift+e', () => this.props.history.push('/s/recentItems'));
      Mousetrap.bind('ctrl+shift+f', () => this.props.history.push('/s/search'));
    },
  }),
  withProps(({
    history, openDocRef, actionBarItems, documentTree,
  }) => ({
    menuItems: [
      {
        key: 'welcome',
        title: 'Welcome',
        onClick: () => history.push(`${pathPrefix}/welcome/`),
        icon: 'home',
        style: 'nav',
      },
      getDocumentTreeMenuItems(d => openDocRef(history, d), documentTree),
      {
        key: 'data',
        title: 'Data',
        onClick: () => history.push(`${pathPrefix}/data`),
        icon: 'database',
        style: 'nav',
      },
      {
        key: 'pipelines',
        title: 'Pipelines',
        onClick: () => history.push(`${pathPrefix}/pipelines`),
        icon: 'tasks',
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
        onClick: () => {},
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
      {
        key: 'recent-items',
        title: 'Recent Items',
        onClick: () => history.push(`${pathPrefix}/recentItems`),
        icon: 'file outline',
        style: 'nav',
      },
      {
        key: 'search',
        title: 'Search',
        onClick: () => history.push(`${pathPrefix}/search`),
        icon: 'search',
        style: 'nav',
      },
    ],
  })),
);

const getExpandedMenuItems = (menuItems, menuItemsOpen, menuItemOpened, depth = 0) =>
  menuItems.map(menuItem => (
    <React.Fragment key={menuItem.key}>
      <div
        className={`sidebar__menu-item--${menuItem.style}`}
        style={{ paddingLeft: `${depth * 0.7}rem` }}
      >
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
      </div>
      {menuItem.children &&
        menuItemsOpen[menuItem.key] &&
        getExpandedMenuItems(menuItem.children, menuItemsOpen, menuItemOpened, depth + 1)}
    </React.Fragment>
  ));

const getContractedMenuItems = menuItems =>
  menuItems.map(menuItem => (
    <React.Fragment key={menuItem.key}>
      {!menuItem.skipInContractedMenu && ( // just put the children of menu items into the sidebar
        <Button key={menuItem.title} icon={menuItem.icon} onClick={menuItem.onClick} />
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
  actionBarItems,
  menuItemsOpen,
  menuItemOpened,
  setIsExpanded,
}) => (
  <div className="app-chrome">
    <div className="app-chrome__sidebar">
      {isExpanded ? (
        <React.Fragment>
          <div>
            <Button
              size="large"
              color={SIDE_BAR_COLOUR}
              icon="bars"
              onClick={() => setIsExpanded(!isExpanded)}
            />
            <img className="sidebar__logo" alt="X" src={require('../../images/logo.svg')} />
          </div>
          <div className="app-chrome__sidebar-menu">
            {getExpandedMenuItems(menuItems, menuItemsOpen, menuItemOpened)}
          </div>
        </React.Fragment>
      ) : (
        <Button.Group vertical color={SIDE_BAR_COLOUR}>
          <Button size="large" icon="bars" onClick={() => setIsExpanded(!isExpanded)} />
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
);

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
