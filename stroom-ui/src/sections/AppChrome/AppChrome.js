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
import { Button, Menu, Icon, Header, Grid } from 'semantic-ui-react';
import Mousetrap from 'mousetrap';

import { actionCreators as appChromeActionCreators } from './redux';
import { actionCreators as recentItemsActionCreators } from 'prototypes/RecentItems/redux';
import { actionCreators as appSearchActionCreators } from 'prototypes/AppSearch/redux';
import { actionCreators as docExplorerActionCreators } from 'components/DocExplorer/redux';
import { withExplorerTree } from 'components/DocExplorer';
import ActionBarItem from './ActionBarItem';
import RecentItems from 'prototypes/RecentItems';
import AppSearch from 'prototypes/AppSearch';
import withLocalStorage from 'lib/withLocalStorage';

const { menuItemOpened } = appChromeActionCreators;
import logoInWhite from './logo_white.png';

const { recentItemsOpened } = recentItemsActionCreators;
const { appSearchOpened } = appSearchActionCreators;
const withIsExpanded = withLocalStorage('isExpanded', 'setIsExpanded', true);

const SIDE_BAR_COLOUR = 'blue';

const pathPrefix = '/s';

const getDocumentTreeMenuItems = (history, treeNode) => ({
  key: treeNode.uuid,
  title: treeNode.name,
  onClick: () => history.push(`/s/doc/${treeNode.type}/${treeNode.uuid}`),
  icon: 'folder',
  children:
    treeNode.children && treeNode.children.length > 0 &&
    treeNode.children.filter(t => t.type === 'Folder').map(t => getDocumentTreeMenuItems(history, t)),
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
      recentItemsOpened,
      appSearchOpened,
    },
  ),
  withRouter,
  withIsExpanded,
  lifecycle({
    componentDidMount() {
      Mousetrap.bind('ctrl+shift+e', () => this.props.recentItemsOpened());
      Mousetrap.bind('ctrl+shift+f', () => this.props.appSearchOpened());
    },
  }),
  withProps(({
    isExpanded,
    setIsExpanded,
    history,
    recentItemsOpened,
    appSearchOpened,
    actionBarItems,
    documentTree,
  }) => ({
    menuItems: [
      {
        key: 'stroom',
        title: <img src={logoInWhite} alt='Stroom logo'/>,
        icon: 'bars',
        onClick: () => setIsExpanded(!isExpanded),
      },
      {
        key: 'welcome',
        title: 'Welcome',
        onClick: () => history.push(`${pathPrefix}/welcome/`),
        icon: 'home',
      },
      {
        key: 'explorer',
        title: 'Explorer',
        onClick: () => history.push(`${pathPrefix}/docExplorer`),
        icon: 'eye',
        children: [getDocumentTreeMenuItems(history, documentTree)],
      },
      {
        key: 'data',
        title: 'Data',
        onClick: () => history.push(`${pathPrefix}/data`),
        icon: 'database',
      },
      {
        key: 'pipelines',
        title: 'Pipelines',
        onClick: () => history.push(`${pathPrefix}/pipelines`),
        icon: 'tasks',
      },
      {
        key: 'processing',
        title: 'Processing',
        onClick: () => history.push(`${pathPrefix}/processing`),
        icon: 'play',
      },
      {
        key: 'admin',
        title: 'Admin',
        onClick: () => console.log('Expand admin'),
        icon: 'cogs',
        children: [
          {
            key: 'admin-me',
            title: 'Me',
            onClick: () => history.push(`${pathPrefix}/me`),
            icon: 'user',
          },
          {
            key: 'admin-users',
            title: 'Users',
            onClick: () => history.push(`${pathPrefix}/users`),
            icon: 'users',
          },
          {
            key: 'admin-apikeys',
            title: 'API Keys',
            onClick: () => history.push(`${pathPrefix}/apikeys`),
            icon: 'key',
          },
        ],
      },
      {
        key: 'recent-items',
        title: 'Recent Items',
        onClick: recentItemsOpened,
        icon: 'file outline',
      },
      {
        key: 'search',
        title: 'Search',
        onClick: appSearchOpened,
        icon: 'search',
      },
    ],
  })),
);

const getMenuItems = (menuItems, menuItemsOpen, menuItemOpened, depth = 0) =>
  menuItems.map(menuItem => (
    <React.Fragment key={menuItem.key}>
      <div className="sidebar__menu-item" style={{ marginLeft: `${depth * 0.7}rem` }} onClick={menuItem.onClick}>
        {menuItem.children ? (
          <Icon
            onClick={() => menuItemOpened(menuItem.key, !menuItemsOpen[menuItem.key])}
            name={`caret ${menuItemsOpen[menuItem.key] ? 'down' : 'right'}`}
          />
        ) : (
          <Icon />
        )}
        <Icon name={menuItem.icon} />
        {menuItem.title}
      </div>
      {menuItem.children &&
        menuItemsOpen[menuItem.key] &&
        getMenuItems(menuItem.children, menuItemsOpen, menuItemOpened, depth + 1)}
    </React.Fragment>
  ));

const AppChrome = ({
  activeMenuItem,
  headerContent,
  icon,
  content,
  isExpanded,
  menuItems,
  actionBarItems,
  menuItemsOpen,
  menuItemOpened,
}) => (
  <div className="app-chrome">
    <AppSearch />
    <RecentItems />
    <div className="app-chrome__sidebar">
      {isExpanded ? (
        <div className="app-chrome__sidebar-menu">
          {getMenuItems(menuItems, menuItemsOpen, menuItemOpened)}
        </div>
      ) : (
        <Button.Group vertical color={SIDE_BAR_COLOUR} size="large">
          {menuItems.map(menuItem => (
            <Button
              key={menuItem.title}
              active={menuItem.title === activeMenuItem}
              icon={menuItem.icon}
              onClick={menuItem.onClick}
            />
          ))}
        </Button.Group>
      )}
    </div>
    <div className="app-chrome__content">
      <div className="content-tabs">
        <div className="content-tabs__content">
          <Grid className="content-tabs__grid">
            <Grid.Column width={5}>
              <Header as="h3">
                <Icon name={icon} color="grey" />
                {headerContent}
              </Header>
            </Grid.Column>
            <Grid.Column width={4}>{actionBarItems}</Grid.Column>
          </Grid>
          {content}
        </div>
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
  icon: PropTypes.string.isRequired,
  headerContent: PropTypes.object.isRequired,
  content: PropTypes.object.isRequired,
  actionBarItems: PropTypes.object,
};

export default enhance(AppChrome);
