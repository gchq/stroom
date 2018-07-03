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
import { Menu } from 'semantic-ui-react';

import DocExplorer from 'components/DocExplorer';
import TrackerDashboard from 'sections/TrackerDashboard';
import DocRefEditor from './DocRefEditor';
import UserSettings from 'prototypes/UserSettings';
import DocRefTabs from './DocRefTabs';
import IFrame from './IFrame';

import { actionCreators, TAB_TYPES } from './redux';
import { withConfig } from 'startup/config';

const { tabWasSelected } = actionCreators;

const enhance = compose(
  withConfig,
  connect(
    (state, props) => ({
      ...state.appChrome,
      authUsersUiUrl: state.config.authUsersUiUrl,
      authTokensUiUrl: state.config.authTokensUiUrl,
    }),
    { tabWasSelected },
  ),
);

const AppContent = enhance(({
  tabWasSelected, selectedTab, openTabs, authUsersUiUrl, authTokensUiUrl,
}) => {
  const tabContents = openTabs.map((tabType) => {
    let tabContent;

    switch (tabType) {
      case TAB_TYPES.OPEN_DOC_REFS:
        tabContent = <DocRefTabs />;
        break;
      case TAB_TYPES.EXPLORER_TREE:
        tabContent = <DocExplorer explorerId="content-tab-tree" />;
        break;
      case TAB_TYPES.TRACKER_DASHBOARD:
        tabContent = <TrackerDashboard />;
        break;
      case TAB_TYPES.USER_ME:
        tabContent = <UserSettings />;
        break;
      case TAB_TYPES.AUTH_USERS:
        tabContent = <IFrame url={authUsersUiUrl} />;
        break;
      case TAB_TYPES.AUTH_TOKENS:
        tabContent = <IFrame url={authTokensUiUrl} />;
        break;
      default:
        // sad time s
        tabContent = <div>Invalid tab</div>;
        break;
    }

    const display = tabType === selectedTab ? 'block' : 'none';
    return (
      <div key={tabType} style={{ display }}>
        {tabContent}
      </div>
    );
  });

  return <div className="app-content">{tabContents}</div>;
});

AppContent.propTypes = {};

export default AppContent;
