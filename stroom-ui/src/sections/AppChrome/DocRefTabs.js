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
import IFrame from './IFrame';

import { actionCreators, TAB_TYPES } from './redux';
import { withConfig } from 'startup/config';

const { docRefTabSelected, docRefClosed } = actionCreators;

const enhance = compose(
  withConfig,
  connect(
    (state, props) => ({
      openDocRefTabs: state.appChrome.openDocRefTabs,
      selectedDocRef: state.appChrome.selectedDocRef,
    }),
    { docRefTabSelected, docRefClosed },
  ),
);

const AppContent = enhance(({
  docRefTabSelected, docRefClosed, selectedDocRef, openDocRefTabs,
}) => {
  const menuItems = openDocRefTabs.map((docRef, index, arr) => {
    const title = docRef.name;

    const closeDocRef = (e) => {
      docRefClosed(docRef);
      e.preventDefault();
    };

    return (
      <Menu.Item
        key={docRef.uuid}
        onClick={() => docRefTabSelected(docRef)}
        active={docRef.uuid === selectedDocRef.uuid}
      >
        {title}
        <button className="doc-ref-content__close-btn" onClick={closeDocRef}>
          x
        </button>
      </Menu.Item>
    );
  });

  const tabContents = openDocRefTabs.map((docRef) => {
    const display = docRef.uuid === selectedDocRef.uuid ? 'block' : 'none';
    return (
      <div key={docRef.uuid} style={{ display }}>
        <DocRefEditor docRef={docRef} />
      </div>
    );
  });

  return (
    <div className="doc-ref-content">
      <Menu pointing secondary color="blue">
        {menuItems}
      </Menu>
      <div className="doc-ref-content__content">{tabContents}</div>
    </div>
  );
});

AppContent.propTypes = {};

export default AppContent;
