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
import { compose, lifecycle } from 'recompose';
import { Button, Menu, Icon } from 'semantic-ui-react';
import Mousetrap from 'mousetrap';
import { withRouter } from 'react-router-dom';

import { actionCreators as appChromeActionCreators } from './redux';
import { actionCreators as recentItemsActionCreators } from 'prototypes/RecentItems/redux';
import { actionCreators as appSearchActionCreators } from 'prototypes/AppSearch/redux';
import TabTypes, { TabTypeDisplayInfo } from './TabTypes';
import AppMainContent from './AppMainContent';
import RecentItems from 'prototypes/RecentItems';
import AppSearch from 'prototypes/AppSearch';
import withLocalStorage from 'lib/withLocalStorage';

const { tabOpened } = appChromeActionCreators;
const { recentItemsOpened } = recentItemsActionCreators;
const { appSearchOpened } = appSearchActionCreators;
const withIsExpanded = withLocalStorage('isExpanded', 'setIsExpanded', true);

const SIDE_BAR_COLOUR = 'blue';

const enhance = compose(
  connect(
    (state, props) => ({
      currentTab: state.appChrome.tabSelectionStack[0],
    }),
    {
      tabOpened,
      recentItemsOpened,
      appSearchOpened,
    },
  ),
  withRouter,
  withIsExpanded,
  lifecycle({
    componentWillMount() {
      if (this.props.match) {
        // We're going to see if we've got a matching tab type to display,
        // and if we have we're going to make sure it opens.
        const { path } = this.props.match;
        const tabType = Object.keys(TabTypeDisplayInfo).find(tabTypeKey => TabTypeDisplayInfo[tabTypeKey].path === path);
        if (tabType) this.props.tabOpened(parseInt(tabType, 10));
      }
    },
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
  currentTab,
  history,
}) => {
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
      onClick: () => {
        // If we open a tab we need to make sure we update the route.
        history.push(TabTypeDisplayInfo[tabType].path);
        tabOpened(tabType);
      },
      selected: currentTab && currentTab.type === tabType,
    })));

  const menu = isExpanded ? (
    <Menu vertical fluid color={SIDE_BAR_COLOUR} inverted>
      {menuItems.map(menuItem => (
        <Menu.Item
          key={menuItem.title}
          active={menuItem.selected}
          name={menuItem.title}
          onClick={menuItem.onClick}
        >
          <Icon name={menuItem.icon} />
          {menuItem.title}
        </Menu.Item>
      ))}
    </Menu>
  ) : (
    <Button.Group vertical color={SIDE_BAR_COLOUR} size="large">
      {menuItems.map(menuItem => (
        <Button
          key={menuItem.title}
          active={menuItem.selected}
          icon={menuItem.icon}
          onClick={menuItem.onClick}
        />
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

AppChrome.contextTypes = {
  store: PropTypes.object,
  router: PropTypes.shape({
    history: object.isRequired,
  }),
};

export default enhance(AppChrome);
