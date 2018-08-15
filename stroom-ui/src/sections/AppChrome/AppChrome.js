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
import MenuItem from './MenuItem';
import {
  MoveDocRefDialog,
  RenameDocRefDialog,
  CopyDocRefDialog,
  DeleteDocRefDialog,
} from 'components/FolderExplorer';

import { actionCreators as userSettingsActionCreators } from 'prototypes/UserSettings';

const { menuItemOpened } = appChromeActionCreators;
const { themeChanged } = userSettingsActionCreators;

const withIsExpanded = withLocalStorage('isExpanded', 'setIsExpanded', true);

const pathPrefix = '/s';

const getDocumentTreeMenuItems = (openDocRef, treeNode, skipInContractedMenu = false) => ({
  key: treeNode.uuid,
  title: treeNode.name,
  onClick: () => openDocRef(treeNode),
  icon: 'folder',
  style: skipInContractedMenu ? 'doc' : 'nav',
  skipInContractedMenu,
  docRef: treeNode,
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
      { userSettings: { theme }, docExplorer: { documentTree }, appChrome: { menuItemsOpen } },
      props,
    ) => ({
      documentTree,
      menuItemsOpen,
      theme,
    }),
    {
      menuItemOpened,
      openDocRef,
      themeChanged,
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
      <MenuItem
        menuItem={menuItem}
        menuItemsOpen={menuItemsOpen}
        menuItemOpened={menuItemOpened}
        depth={depth}
      />
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
  theme,
  themeChanged,
}) => {
  if (theme === undefined) {
    theme = 'theme-light';
    themeChanged(theme);
  }
  return (
    <div className={`app-container ${theme}`}>
      <div className="app-chrome">
        <MoveDocRefDialog />
        <RenameDocRefDialog />
        <DeleteDocRefDialog />
        <CopyDocRefDialog />
        <div className="app-chrome__sidebar">
          {isExpanded ? (
            <React.Fragment>
              <div className="app-chrome__sidebar_header">
                <Button
                  aria-label="Show/hide the sidebar"
                  size="large"
                  className="app-chrome__sidebar__toggle"
                  icon="bars"
                  onClick={() => setIsExpanded(!isExpanded)}
                />
                <div className="sidebar__logo" alt="Stroom logo">
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    width="100"
                    height="30"
                    viewBox="0 0 2278.5303 617.98476"
                  >
                    <path d="M 121.99991,205.98497 C 54.999958,205.98497 0,259.98493 0,327.98487 0,394.98481 54.999958,449.98476 121.99991,449.98476 l 167.99987,0 c 24.99998,0 44.99996,20.99998 44.99996,44.99996 0,24.99998 -19.99998,44.99996 -44.99996,44.99996 l -180.79049,0 C 65.984126,553.87219 27.100843,581.57313 0.01093599,617.98462 l 289.98884401,0 c 66.99994,0 121.99991,-54.99996 121.99991,-122.9999 0,-66.99994 -54.99997,-121.99989 -121.99991,-121.99989 l -167.99987,0 c -24.999984,0 -44.999969,-19.99999 -44.999969,-44.99996 0,-24.99998 19.999985,-44.99996 44.999969,-44.99996 l 228.79045,0 c 26.9905,-35.88325 65.47093,-63.18547 108.21554,-76.99994 l -337.00599,0 z" />
                    <path d="M 601.44485,-7.7860649e-4 C 572.6583,8.3697982 546.49491,22.822962 524.44491,41.902305 l 0,163.887385 0.002,0.19528 -0.002,0 c -65.11,4.8e-4 -123.03145,30.0199 -160.74675,76.99994 l 160.74675,0 0,334.99971 76.99994,0 0,-334.99971 144.85298,0 c 27.16134,-36.05885 65.96411,-63.39275 109.02184,-76.99994 l -253.87482,0 0,-205.98574860649 z" />
                    <path d="m 919.42586,205.98497 c -113.99519,0 -205.99537,92.00648 -206.00296,205.99982 -1.5e-4,68.67178 0,137.32749 0,205.99983 l 77.99994,0 c -0.012,-68.6493 0.0176,-137.46869 0,-205.99983 0.0846,-70.92785 57.05747,-128.99988 128.00462,-128.99988 5.28623,0 10.49823,0.32864 15.62183,0.95311 18.2219,-24.51229 41.78461,-45.0838 68.42501,-60.15146 -25.65065,-11.43799 -54.08662,-17.80159 -84.04684,-17.80159 l 0,0 z" />
                    <path d="m 1109.4153,205.98457 c -113.99994,0 -205.99983,91.99993 -205.99983,205.99984 0,112.9999 91.99989,205.9998 205.99983,205.9998 114,0 205.9999,-92.9999 205.9999,-205.9998 0,-113.99991 -91.9999,-205.99984 -205.9999,-205.99984 z m 0,333.99974 c -70.9999,0 -128.99994,-56.99996 -128.99994,-127.9999 0,-70.99995 58.00004,-128.9999 128.99994,-128.9999 71,0 128.9999,57.99995 128.9999,128.9999 0,70.99994 -57.9999,127.9999 -128.9999,127.9999 z" />
                    <path d="m 1444.4737,205.98457 c -113.9999,0 -205.9998,91.99993 -205.9998,205.99984 0,112.9999 91.9999,205.9998 205.9998,205.9998 113.9999,0 205.9999,-92.9999 205.9999,-205.9998 0,-113.99991 -92,-205.99984 -205.9999,-205.99984 z m 0,333.99974 c -70.9999,0 -128.9999,-56.99996 -128.9999,-127.9999 0,-70.99995 58,-128.9999 128.9999,-128.9999 71,0 128.9999,57.99995 128.9999,128.9999 0,70.99994 -57.9999,127.9999 -128.9999,127.9999 z" />
                    <path d="m 1738.5308,379.98447 c 0,-53 43,-97 95.9999,-97 53,0 97,44 97,97 l 0,237.9997 76.9999,0 0,-237.9997 c 0,-53 43,-97 96.9999,-97 53,0 95.9999,44 95.9999,97 l 0,237.9997 77,0 0,-237.9997 c 0,-96 -77,-173.9999 -172.9999,-173.9999 -54.9999,0 -103.9999,24.99998 -135.9999,64.99995 -31.9999,-39.99997 -79.9999,-64.99995 -134.9999,-64.99995 -95.9999,0 -173.9998,77.9999 -173.9998,173.9999 l 0,237.9997 77.9999,0 z" />
                  </svg>
                </div>
              </div>
              <div className="app-chrome__sidebar-menu">
                {getExpandedMenuItems(menuItems, menuItemsOpen, menuItemOpened)}
              </div>
            </React.Fragment>
          ) : (
            <Button.Group vertical className="app-chrome__sidebar__buttons">
              <Button
                size="large"
                icon="bars"
                className="app-chrome__sidebar__toggle_collapsed"
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
