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

import { actionCreators, TAB_TYPES } from './redux';

const { tabSelected, tabClosed } = actionCreators;

const enhance = compose(connect(
  (state, props) => ({
    ...state.appChrome,
  }),
  { tabSelected, tabClosed },
));

const ContentTabs = enhance(({
  tabSelected, tabClosed, openTabs, tabSelectionStack,
}) => {
  let tabIdSelected;
  if (tabSelectionStack.length > 0) {
    tabIdSelected = tabSelectionStack[tabSelectionStack.length - 1];
  }
  console.log('Tab Selected', tabIdSelected);

  const menuItems = openTabs.map((openTab, index, arr) => {
    let title;

    switch (openTab.type) {
      case TAB_TYPES.DOC_REF:
        const docRef = openTab.data;
        title = docRef.name;
        break;
      case TAB_TYPES.EXPLORER_TREE:
        title = 'Explorer';
        break;
      case TAB_TYPES.TRACKER_DASHBOARD:
        title = 'Trackers';
        break;
      default:
        // sad times
        title = 'UNKNOWN';
        break;
    }

    const closeTab = (e) => {
      tabClosed(openTab.tabId);
      e.preventDefault();
    }

    return (
      <Menu.Item
        key={openTab.tabId}
        onClick={() => tabSelected(openTab.tabId)}
        active={openTab.tabId === tabIdSelected}
      >
        {title}
        <button className="content-tabs__close-btn" onClick={closeTab}>
          x
        </button>
      </Menu.Item>
    );
  });

  let tabContent;
  const selectedTab = openTabs.find(t => t.tabId === tabIdSelected);
  if (selectedTab) {
    switch (selectedTab.type) {
      case TAB_TYPES.DOC_REF:
        tabContent = <DocRefEditor docRef={selectedTab.data} />;
        break;
      case TAB_TYPES.EXPLORER_TREE:
        tabContent = <DocExplorer explorerId="content-tab-tree" />;
        break;
      case TAB_TYPES.TRACKER_DASHBOARD:
        tabContent = <TrackerDashboard />;
        break;
      default:
        // sad times
        tabContent = <div>Invalid tab</div>;
        break;
    }
  }

  return (
    <div className="content-tabs">
      <Menu tabular>{menuItems}</Menu>
      <div className="content-tabs__content">{tabContent}</div>
    </div>
  );
});

ContentTabs.propTypes = {};

export default ContentTabs;
