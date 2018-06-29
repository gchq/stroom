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

import { DocExplorer } from 'components/DocExplorer';
import DocRefEditor from './DocRefEditor';

import { actionCreators, TAB_TYPES } from './redux';

const { tabClosed } = actionCreators;

const enhance = compose(connect(
  (state, props) => ({
    openTabs: state.appChrome.openTabs,
  }),
  { tabClosed },
));

const ContentTabs = enhance(({ tabClosed, openTabs }) => {
  const panes = openTabs.map((t) => {
    let paneContent;
    let title;

    switch (t.type) {
      case TAB_TYPES.DOC_REF:
        const docRef = t.data;
        title = docRef.name;
        paneContent = <DocRefEditor docRef={docRef} />;
        break;
      case TAB_TYPES.EXPLORER_TREE:
        title = 'Explorer';
        paneContent = <DocExplorer explorerId="content-tab-tree" />;
        break;
      default:
        // sad times
        paneContent = <div>Invalid tab</div>;
        break;
    }

    return {
      menuItem: (
        <Menu.Item key={t.tabUuid}>
          {title}
          <button className="close-btn" onClick={() => tabClosed(t.tabUuid)}>
            x
          </button>
        </Menu.Item>
      ),
      pane: <Tab.Pane key={t.tabUuid}>{paneContent}</Tab.Pane>,
    };
  });

  return panes.length > 0 ? (
    <Tab renderActiveOnly={false} panes={panes} />
  ) : (
    <div className="fill-space" />
  );
});

ContentTabs.propTypes = {};

export default ContentTabs;
