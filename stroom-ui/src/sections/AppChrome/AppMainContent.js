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
import { compose } from 'recompose';
import { Menu, Header, Icon, Divider, Grid, Button, Popup } from 'semantic-ui-react';

import DocExplorer from 'components/DocExplorer';
import TrackerDashboard from 'sections/TrackerDashboard';
import DocRefEditor from './DocRefEditor';
import UserSettings from 'prototypes/UserSettings';
import IFrame from './IFrame';

import { actionCreators as appChromeActionCreators } from './redux';
import { actionCreators as appSearchActionCreators } from 'prototypes/AppSearch/redux';
import { actionCreators as recentItemsActionCreators } from 'prototypes/RecentItems/redux';

import TabTypes, { TabTypeDisplayInfo } from './TabTypes';
import { withConfig } from 'startup/config';

const { appSearchOpened } = appSearchActionCreators;
const { recentItemsOpened } = recentItemsActionCreators;
const { tabSelected, tabClosed } = appChromeActionCreators;

const enhance = compose(
  withConfig,
  connect(
    (state, props) => ({
      ...state.appChrome,
      authUsersUiUrl: state.config.authUsersUiUrl,
      authTokensUiUrl: state.config.authTokensUiUrl,
    }),
    { tabSelected, tabClosed, appSearchOpened, recentItemsOpened },
  ),
);

const AppMainContent = ({
  tabSelected,
  tabClosed,
  appSearchOpened,
  recentItemsOpened,
  openTabs,
  tabSelectionStack,
  authUsersUiUrl,
  authTokensUiUrl,
}) => {
  let selectedTab;
  if (tabSelectionStack.length > 0) {
    selectedTab = tabSelectionStack[0];
  }

  const menuItems = openTabs.map((openTab, index, arr) => {
    const title = TabTypeDisplayInfo[openTab.type].getTitle(openTab.data);

    const closeTab = (e) => {
      tabClosed(openTab.tabId);
      e.preventDefault();
    };

    return (
      <Menu.Item
        key={openTab.tabId}
        onClick={() => tabSelected(openTab.tabId)}
        active={openTab.tabId === selectedTab.tabId}
      >
        {title}
        <button className="content-tabs__close-btn" onClick={closeTab}>
          x
        </button>
      </Menu.Item>
    );
  });

  const tabContents = openTabs.map((openTab) => {
    let tabContent;

    switch (openTab.type) {
      case TabTypes.DOC_REF:
        tabContent = <DocRefEditor docRef={openTab.data} />;
        break;
      case TabTypes.EXPLORER_TREE:
        tabContent = <DocExplorer explorerId="content-tab-tree" />;
        break;
      case TabTypes.TRACKER_DASHBOARD:
        tabContent = <TrackerDashboard />;
        break;
      case TabTypes.USER_ME:
        tabContent = <UserSettings />;
        break;
      case TabTypes.AUTH_USERS:
        tabContent = <IFrame url={authUsersUiUrl} />;
        break;
      case TabTypes.AUTH_TOKENS:
        tabContent = <IFrame url={authTokensUiUrl} />;
        break;
      default:
        // sad time s
        tabContent = <div>Invalid tab</div>;
        break;
    }

    return (
      <div
        className={`content-tabs__tab${
          openTab.tabId === selectedTab.tabId ? '--chosen' : '--hidden'
        }`}
        key={openTab.tabId}
      >
        <Grid>
          <Grid.Column width={12}>
            <Header as="h3">
              <Icon name={TabTypeDisplayInfo[selectedTab.type].icon} color="grey" />
              {TabTypeDisplayInfo[selectedTab.type].getTitle(selectedTab.data)}
            </Header>
          </Grid.Column>
          <Grid.Column width={4}>
            <Popup trigger={<Button floated="right" circular icon="file outline" onClick={() => recentItemsOpened()}/>} content="Recently opened items"/>
            <Popup trigger={<Button floated="right" circular icon="search" onClick={()=> appSearchOpened()}/>} content="Search for things"/>
          </Grid.Column>
        </Grid>
        <Divider />
        {tabContent}
      </div>
    );
  });

  return (
    <div className="content-tabs">
      {/* <Menu tabular>{menuItems}</Menu> */}
      <div className="content-tabs__content">{tabContents}</div>
    </div>
  );
};

AppMainContent.propTypes = {};

export default enhance(AppMainContent);
