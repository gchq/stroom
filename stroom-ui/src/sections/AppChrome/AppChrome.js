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
import { compose, withState, lifecycle } from 'recompose';
import { Button, Menu, Icon } from 'semantic-ui-react';
import Mousetrap from 'mousetrap';

import { actionCreators as appChromeActionCreators } from './redux';
import { actionCreators as recentItemsActionCreators } from 'prototypes/RecentItems/redux';
import { actionCreators as appSearchActionCreators } from 'prototypes/AppSearch/redux';
import TabTypes, { TabTypeDisplayInfo } from './TabTypes';
import AppMainContent from './AppMainContent';
import RecentItems from 'prototypes/RecentItems';
import AppSearch from 'prototypes/AppSearch';

const { tabOpened } = appChromeActionCreators;
const { recentItemsOpened } = recentItemsActionCreators;
const { appSearchOpened } = appSearchActionCreators;
const withIsExpanded = withState('isExpanded', 'setIsExpanded', false);

const enhance = compose(
  connect((state, props) => ({}), {
    tabOpened,
    recentItemsOpened,
    appSearchOpened,
  }),
  withIsExpanded,
  lifecycle({
    componentDidMount() {
      Mousetrap.bind('ctrl+e', () => this.props.recentItemsOpened());
      Mousetrap.bind('ctrl+f', () => this.props.appSearchOpened());
    },
  }),
);

const AppChrome = ({
  tabOpened,
  recentItemsOpened,
  appSearchOpened,
  isExpanded,
  setIsExpanded,
}) => {
  // This sets the default tab that opens when the app opens.
  // TODO: It should probably be configurable. Maybe we could store their most recent tab in localStorage.
  tabOpened(TabTypes.EXPLORER_TREE);

  const menuItems = [
    {
      title: 'Stroom',
      icon: 'bars',
      onClick: () => setIsExpanded(!isExpanded),
    },
  ].concat(Object.values(TabTypes)
    .filter(t => t !== TabTypes.DOC_REF) // this type is used to cover individual open doc refs
    .map(tabType => ({
      title: TabTypeDisplayInfo[tabType].getTitle(),
      icon: TabTypeDisplayInfo[tabType].icon,
      onClick: () => tabOpened(tabType),
    })));

  const menu = isExpanded ? (
    <Menu vertical fluid color="blue" inverted>
      {menuItems.map(menuItem => (
        <Menu.Item key={menuItem.title} name={menuItem.title} onClick={menuItem.onClick}>
          <Icon name={menuItem.icon} />
          {menuItem.title}
        </Menu.Item>
      ))}
    </Menu>
  ) : (
    <Button.Group vertical color="blue" size="large">
      {menuItems.map(menuItem => (
        <Button key={menuItem.title} icon={menuItem.icon} onClick={menuItem.onClick} />
      ))}
    </Button.Group>
  );

  return (
    <div className="app-chrome">
      <div className="app-chrome__sidebar">{menu}</div>
      <div className="app-chrome__content">
        <AppMainContent />
        <RecentItems />
        <AppSearch />
      </div>
    </div>
  );
};

export default enhance(AppChrome);
