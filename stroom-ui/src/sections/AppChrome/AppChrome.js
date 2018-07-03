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

import { actionCreators, TAB_TYPES } from './redux';
import AppContent from './AppContent';

const { tabWasSelected } = actionCreators;
const withIsExpanded = withState('isExpanded', 'setIsExpanded', false);

const enhance = compose(
  connect(
    (state, props) => ({
      selectedTab: state.appChrome.selectedTab,
    }),
    {
      tabWasSelected,
    },
  ),
  withIsExpanded,
);

const AppChrome = enhance(({
  tabWasSelected, selectedTab, isExpanded, setIsExpanded,
}) => {
  const toggleExpanded = () => setIsExpanded(!isExpanded);

  const menuItems = [
    {
      name: 'Open Docs',
      icon: 'file outline',
      tab: TAB_TYPES.OPEN_DOC_REFS,
    },
    {
      name: 'Explorer',
      icon: 'eye',
      tab: TAB_TYPES.EXPLORER_TREE,
    },
    {
      name: 'Trackers',
      icon: 'tasks',
      tab: TAB_TYPES.TRACKER_DASHBOARD,
    },
    {
      name: 'User',
      icon: 'user',
      tab: TAB_TYPES.USER_ME,
    },
    {
      name: 'Users',
      icon: 'users',
      tab: TAB_TYPES.AUTH_USERS,
    },
    {
      name: 'API Keys',
      icon: 'key',
      tab: TAB_TYPES.AUTH_TOKENS,
    },
  ];

  const menu = isExpanded ? (
    <Menu pointing secondary vertical color="blue" inverted>
      <Menu.Item onClick={toggleExpanded}>
        <Icon name="bars" />
        Stroom
      </Menu.Item>
      {menuItems.map(menuItem => (
        <Menu.Item
          active={selectedTab === menuItem.tab}
          key={menuItem.name}
          name={menuItem.name}
          onClick={() => tabWasSelected(menuItem.tab)}
        >
          <Icon name={menuItem.icon} />
          {menuItem.name}
        </Menu.Item>
      ))}
    </Menu>
  ) : (
    <Button.Group vertical color="blue" size="large">
      <Button icon="bars" onClick={toggleExpanded} />
      {menuItems.map(menuItem => (
        <Button
          key={menuItem.name}
          icon={menuItem.icon}
          onClick={() => tabWasSelected(menuItem.tab)}
        />
      ))}
    </Button.Group>
  );

  return (
    <div className="app-chrome">
      <div className="app-chrome__sidebar">{menu}</div>
      <div className="app-chrome__content">
        <AppContent />
      </div>
    </div>
  );
});

export default AppChrome;
