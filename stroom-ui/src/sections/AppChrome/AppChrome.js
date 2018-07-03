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
import { connect } from 'react-redux';
import { compose, withState } from 'recompose';
import { Button, Menu, Icon } from 'semantic-ui-react';

import { actionCreators } from './redux';
import TabTypes from './TabTypes';
import AppMainContent from './AppMainContent';
import RecentItems from './RecentItems';

const { tabOpened, openRecentItems } = actionCreators;
const withIsExpanded = withState('isExpanded', 'setIsExpanded', false);

const enhance = compose(
  connect((state, props) => ({}), {
    tabOpened,
    openRecentItems,
  }),
  withIsExpanded,
);

const AppChrome = enhance(({
  tabOpened, openRecentItems, isExpanded, setIsExpanded,
}) => {
  const menuItems = [
    {
      name: 'Stroom',
      icon: 'bars',
      onClick: () => setIsExpanded(!isExpanded),
    },
    {
      name: 'Open Doc Ref',
      icon: 'file outline',
      onClick: openRecentItems,
    },
    {
      name: 'Explorer',
      icon: 'eye',
      onClick: () => tabOpened(TabTypes.EXPLORER_TREE),
    },
    {
      name: 'Trackers',
      icon: 'tasks',
      onClick: () => tabOpened(TabTypes.TRACKER_DASHBOARD),
    },
    {
      name: 'User',
      icon: 'user',
      onClick: () => tabOpened(TabTypes.USER_ME),
    },
    {
      name: 'Users',
      icon: 'users',
      onClick: () => tabOpened(TabTypes.AUTH_USERS),
    },
    {
      name: 'API Keys',
      icon: 'key',
      onClick: () => tabOpened(TabTypes.AUTH_TOKENS),
    },
  ];

  const menu = isExpanded ? (
    <Menu vertical fluid color="blue" inverted>
      {menuItems.map(menuItem => (
        <Menu.Item key={menuItem.name} name={menuItem.name} onClick={menuItem.onClick}>
          <Icon name={menuItem.icon} />
          {menuItem.name}
        </Menu.Item>
      ))}
    </Menu>
  ) : (
    <Button.Group vertical color="blue" size="large">
      {menuItems.map(menuItem => (
        <Button key={menuItem.name} icon={menuItem.icon} onClick={menuItem.onClick} />
      ))}
    </Button.Group>
  );

  return (
    <div className="app-chrome">
      <div className="app-chrome__sidebar">{menu}</div>
      <div className="app-chrome__content">
        <AppMainContent />
        <RecentItems />
      </div>
    </div>
  );
});

export default AppChrome;
