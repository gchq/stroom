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
import { withRouter } from 'react-router-dom';
import { Button, Menu, Icon, Header, Divider, Grid, Popup } from 'semantic-ui-react';
import Mousetrap from 'mousetrap';

import { actionCreators as recentItemsActionCreators } from 'prototypes/RecentItems/redux';
import { actionCreators as appSearchActionCreators } from 'prototypes/AppSearch/redux';
import RecentItems from 'prototypes/RecentItems';
import AppSearch from 'prototypes/AppSearch';
import withLocalStorage from 'lib/withLocalStorage';

const { recentItemsOpened } = recentItemsActionCreators;
const { appSearchOpened } = appSearchActionCreators;
const withIsExpanded = withLocalStorage('isExpanded', 'setIsExpanded', true);

const SIDE_BAR_COLOUR = 'blue';

const enhance = compose(
  connect((state, props) => ({}), {
    recentItemsOpened,
    appSearchOpened,
  }),
  withRouter,
  withIsExpanded,
  lifecycle({
    componentDidMount() {
      Mousetrap.bind('ctrl+e', () => this.props.recentItemsOpened());
      Mousetrap.bind('ctrl+f', () => this.props.appSearchOpened());
    },
  }),
);

const pathPrefix = '/s';

const sidebarMenuItems = [
  {
    title: 'Welcome',
    path: `${pathPrefix}/welcome/`,
    icon: 'home',
  },
  {
    title: 'Explorer',
    path: `${pathPrefix}/docExplorer`,
    icon: 'eye',
  },
  {
    title: 'Data',
    path: `${pathPrefix}/data`,
    icon: 'database',
  },
  {
    title: 'Pipelines',
    path: `${pathPrefix}/pipelines`,
    icon: 'tasks',
  },
  {
    title: 'Processing',
    path: `${pathPrefix}/trackers`, // TODO change this to processing -- needs changing in the stroom iFrame too.
    icon: 'play',
  },
  {
    title: 'Me',
    path: `${pathPrefix}/me`,
    icon: 'user',
  },
  {
    title: 'Users',
    path: `${pathPrefix}/users`,
    icon: 'users',
  },
  {
    title: 'API Keys',
    path: `${pathPrefix}/apikeys`,
    icon: 'key',
  },
];

const AppChrome = ({
  title,
  icon,
  recentItemsOpened,
  appSearchOpened,
  isExpanded,
  setIsExpanded,
  history,
  children,
}) => {
  const menuItems = [
    {
      title: 'Stroom',
      icon: 'bars',
      onClick: () => setIsExpanded(!isExpanded),
    },
  ].concat(sidebarMenuItems.map(sidebarMenuItem => ({
    title: sidebarMenuItem.title,
    icon: sidebarMenuItem.icon,
    onClick: () => history.push(sidebarMenuItem.path),
  })));

  const menu = isExpanded ? (
    <Menu vertical fluid color={SIDE_BAR_COLOUR} inverted>
      {menuItems.map(menuItem => (
        <Menu.Item
          key={menuItem.title}
          active={menuItem.title === title}
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
          active={menuItem.title === title}
          icon={menuItem.icon}
          onClick={menuItem.onClick}
        />
      ))}
    </Button.Group>
  );

  return (
    <div className="app-chrome">
      <AppSearch />
      <div className="app-chrome__sidebar">{menu}</div>
      <div className="app-chrome__content">
        <div className="content-tabs">
          <div className="content-tabs__content">
            <Grid>
              <Grid.Column width={12}>
                <Header as="h3">
                  <Icon name={icon} color="grey" />
                  {title}
                </Header>
              </Grid.Column>
              <Grid.Column width={4}>
                <Popup
                  trigger={
                    <Button
                      floated="right"
                      circular
                      icon="file outline"
                      onClick={() => recentItemsOpened()}
                    />
                  }
                  content="Recently opened items"
                />
                <Popup
                  trigger={
                    <Button
                      floated="right"
                      circular
                      icon="search"
                      onClick={() => appSearchOpened()}
                    />
                  }
                  content="Search for things"
                />
              </Grid.Column>
            </Grid>
            <Divider />
            {children}
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

AppChrome.propTypes = {};

export default enhance(AppChrome);
