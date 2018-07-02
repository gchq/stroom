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
import { Tab, Menu } from 'semantic-ui-react';

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
  tabSelected, tabClosed, openTabs, tabIdSelected,
}) => {
  let activeIndex;
  const panes = openTabs.map((openTab, index, arr) => {
    if (openTab.tabId === tabIdSelected) {
      activeIndex = index;
    }

    let paneContent;
    let title;

    switch (openTab.type) {
      case TAB_TYPES.DOC_REF:
        const docRef = openTab.data;
        title = docRef.name;
        paneContent = <DocRefEditor docRef={docRef} />;
        break;
      case TAB_TYPES.EXPLORER_TREE:
        title = 'Explorer';
        paneContent = <DocExplorer explorerId="content-tab-tree" />;
        break;
      case TAB_TYPES.TRACKER_DASHBOARD:
        title = 'Trackers';
        paneContent = <TrackerDashboard />;
        break;
      default:
        // sad times
        paneContent = <div>Invalid tab</div>;
        break;
    }

    return {
      menuItem: (
        <Menu.Item key={openTab.tabId}>
          {title}
          <button className="content-tabs__close-btn" onClick={() => tabClosed(openTab.tabId)}>
            x
          </button>
        </Menu.Item>
      ),
      pane: <Tab.Pane key={openTab.tabId}>{paneContent}</Tab.Pane>,
    };
  });

  const handleTabChange = (e, { activeIndex }) => tabSelected(openTabs[activeIndex].tabId);

  return panes.length > 0 ? (
    <Tab
      activeIndex={activeIndex}
      renderActiveOnly={false}
      panes={panes}
      onTabChange={handleTabChange}
    />
  ) : (
    <div className="fill-space" />
  );
});

ContentTabs.propTypes = {};

export default ContentTabs;
