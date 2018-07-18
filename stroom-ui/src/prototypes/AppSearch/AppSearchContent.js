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

import React, { Component } from 'react';

import { compose, lifecycle } from 'recompose';
import { connect } from 'react-redux';
import ReactModal from 'react-modal';
import { Button, Header, Icon, Modal, Menu, Input, Breadcrumb } from 'semantic-ui-react';
import { withRouter } from 'react-router-dom';
import Mousetrap from 'mousetrap';

import { actionCreators as appSearchActionCreators } from './redux';
import withExplorerTree from 'components/DocExplorer/withExplorerTree';
import { openDocRef } from 'prototypes/RecentItems';

const {
  appSearchClosed,
  appSearchTermUpdated,
  appSearchSelectionUp,
  appSearchSelectionDown,
} = appSearchActionCreators;

const enhance = compose(
  withRouter,
  withExplorerTree,
  connect(
    (state, props) => ({
      isOpen: state.appSearch.isOpen,
      searchTerm: state.appSearch.searchTerm,
      searchResults: state.appSearch.searchResults,
      selectedItem: state.appSearch.selectedItem,
    }),
    {
      appSearchClosed,
      appSearchTermUpdated,
      appSearchSelectionUp,
      appSearchSelectionDown,
      openDocRef,
    },
  ),
  lifecycle({
    componentDidMount() {
      // We need to prevent up and down keys from moving the cursor around in the input
      const input = document.getElementById('AppSearch__search-input');
      input.addEventListener(
        'keydown',
        (event) => {
          if (event.keyCode === 38) {
            this.props.appSearchSelectionUp();
            event.preventDefault();
          } else if (event.keyCode === 40) {
            this.props.appSearchSelectionDown();
            event.preventDefault();
          }
        },
        false,
      );
    },
  }),
);

const AppSearchContent = ({
  isOpen,
  searchTerm,
  appSearchClosed,
  appSearchTermUpdated,
  searchResults,
  selectedItem,
  history,
  openDocRef,
  appSearchSelectionUp,
  appSearchSelectionDown,
}) => {
  return (
    <React.Fragment>
      <Input
        id="AppSearch__search-input"
        icon="search"
        placeholder="Search..."
        value={searchTerm}
        onChange={e => appSearchTermUpdated(e.target.value)}
        autoFocus
      />
      <Menu vertical fluid>
        {searchResults.map((searchResult, i, arr) => {
          // Compose the data that provides the breadcrumb to this node
          const sections = searchResult.lineage.map(l => ({
            key: l.name,
            content: l.name,
            link: false,
          }));

          return (
            <Menu.Item
              active={selectedItem === i}
              key={i}
              onClick={() => {
                openDocRef(history, searchResult);
                appSearchClosed();
              }}
            >
              <div style={{ width: '50rem' }}>
                <Breadcrumb size="mini" icon="right angle" sections={sections} />
                <div className="doc-ref-dropdown__item-name">{searchResult.name}</div>
              </div>
            </Menu.Item>
          );
        })}
      </Menu>
    </React.Fragment>
  );
};

export default enhance(AppSearchContent);
