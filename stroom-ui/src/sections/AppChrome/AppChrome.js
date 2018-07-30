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

import { actionCreators as recentItemsActionCreators } from 'prototypes/RecentItems/redux';
import { actionCreators as appSearchActionCreators } from 'prototypes/AppSearch/redux';
import { actionCreators as newDocActionCreators } from 'prototypes/NewDocDialog/redux';
import ActionBarItem from './ActionBarItem';
import NewDocDialog from 'prototypes/NewDocDialog';
import RecentItems from 'prototypes/RecentItems';
import AppSearch from 'prototypes/AppSearch';
import withLocalStorage from 'lib/withLocalStorage';

const { recentItemsOpened } = recentItemsActionCreators;
const { appSearchOpened } = appSearchActionCreators;
const { startDocRefCreation } = newDocActionCreators;
const withIsExpanded = withLocalStorage('isExpanded', 'setIsExpanded', true);

const SIDE_BAR_COLOUR = 'blue';

const pathPrefix = '/s';

const enhance = compose(
  connect((state, props) => ({}), {
    recentItemsOpened,
    appSearchOpened,
    startDocRefCreation,
  }),
  withRouter,
  withIsExpanded,
  lifecycle({
    componentDidMount() {
      Mousetrap.bind('ctrl+shift+e', () => this.props.recentItemsOpened());
      Mousetrap.bind('ctrl+shift+f', () => this.props.appSearchOpened());
      Mousetrap.bind('ctrl+shift+n', () => this.props.startDocRefCreation());
    },
  }),
  withProps(({
    isExpanded,
    setIsExpanded,
    history,
    recentItemsOpened,
    appSearchOpened,
    startDocRefCreation,
    actionBarItems,
  }) => ({
    menuItems: [
      {
        title: 'Stroom',
        icon: 'bars',
        onClick: () => setIsExpanded(!isExpanded),
      },
    ].concat([
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
        path: `${pathPrefix}/processing`,
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
    ].map(menuLink => ({
      title: menuLink.title,
      icon: menuLink.icon,
      onClick: () => history.push(menuLink.path),
    }))),
    actionBarItems: [
      {
        key: 'recentItems',
        onClick: recentItemsOpened,
        icon: 'file outline',
        content: 'Recently opened items',
      },
      {
        key: 'search',
        onClick: appSearchOpened,
        icon: 'search',
        content: 'Search for things',
      },
      {
        key: 'create_doc_ref',
        onClick: startDocRefCreation,
        icon: 'plus',
        content: 'Create a new Doc Ref',
      },
    ],
  })),
);

const AppChrome = ({
  activeMenuItem,
  headerContent,
  icon,
  content,
  actionBarItems,
  isExpanded,
  menuItems,
  actionBarAdditionalItems,
}) => (
  <div className="app-chrome">
    <AppSearch />
    <RecentItems />
    <NewDocDialog />
    <div className="app-chrome__sidebar">
      {isExpanded ? (
        <Menu vertical fluid color={SIDE_BAR_COLOUR} inverted>
          {menuItems.map(menuItem => (
            <Menu.Item
              key={menuItem.title}
              active={menuItem.title === activeMenuItem}
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
          <Grid>
            <Grid.Column width={5}>
              <Header as="h3">
                <Icon name={icon} color="grey" />
                {headerContent}
              </Header>
            </Grid.Column>
            <Grid.Column width={7}>{actionBarAdditionalItems}</Grid.Column>
            <Grid.Column width={4}>
              {actionBarItems.map(aBarItem => (
                <ActionBarItem
                  key={aBarItem.key}
                  onClick={aBarItem.onClick}
                  content={aBarItem.content}
                  buttonProps={{
                    icon: aBarItem.icon,
                  }}
                />
              ))}
            </Grid.Column>
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
  actionBarAdditionalItems: PropTypes.object,
};

export default enhance(AppChrome);
