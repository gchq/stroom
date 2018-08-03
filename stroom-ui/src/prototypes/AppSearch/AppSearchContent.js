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

import { compose } from 'recompose';
import { connect } from 'react-redux';
import { Menu, Input, Breadcrumb, Popup, Button } from 'semantic-ui-react';
import { withRouter } from 'react-router-dom';

import { actionCreators as appSearchActionCreators } from './redux';
import { withExplorerTree } from 'components/DocExplorer';
import { DocTypeFilters } from 'components/DocRefTypes';
import { openDocRef } from 'prototypes/RecentItems';

/**
 * This component is separate to AppSearch.js because the modal in AppSearch.js makes it
 * impossible to add an event listener to the input -- it's not in the DOM at the time of
 * any of the lifecycle methods. Moving the content of the modal to a separate component,
 * this one, solves this problem.
 */

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
      selectedDocRef: state.appSearch.selectedDocRef,
    }),
    {
      appSearchClosed,
      appSearchTermUpdated,
      appSearchSelectionUp,
      appSearchSelectionDown,
      openDocRef,
    },
  ),
);

class AppSearchContent extends React.Component {
  componentDidMount() {
    // We need to prevent up and down keys from moving the cursor around in the input

    // I'd rather use Mousetrap for these shortcut keys. Historically Mousetrap
    // hasn't handled keypresses that occured inside inputs or textareas.
    // There were some changes to fix this, like binding specifically
    // to a field. But that requires getting the element from the DOM and
    // we'd rather not break outside React to do this. The other alternative
    // is adding 'mousetrap' as a class to the input, but that doesn't seem to work.

    // Up
    const upKeycode = 38;
    const kKeycode = 75;

    // Down
    const downKeycode = 40;
    const jKeycode = 74;

    const enterKeycode = 13;

    this.refs.searchTermInput.inputRef.addEventListener(
      'keydown',
      (event) => {
        if (event.keyCode === upKeycode || (event.ctrlKey && event.keyCode === kKeycode)) {
          this.props.appSearchSelectionUp();
          event.preventDefault();
        } else if (event.keyCode === downKeycode || (event.ctrlKey && event.keyCode === jKeycode)) {
          this.props.appSearchSelectionDown();
          event.preventDefault();
        } else if (event.keyCode === enterKeycode) {
          this.props.openDocRef(this.props.history, this.props.selectedDocRef);
          this.props.appSearchClosed();
          event.preventDefault();
        }
      },
      false,
    );
  }

  render() {
    const {
      searchTerm,
      appSearchClosed,
      appSearchTermUpdated,
      searchResults,
      selectedItem,
      history,
      openDocRef,
    } = this.props;
    return (
      <React.Fragment>
        <Input
          id="AppSearch__search-input"
          icon="search"
          placeholder="Search..."
          value={searchTerm}
          onChange={e => appSearchTermUpdated(e.target.value)}
          ref="searchTermInput"
          autoFocus
        />
        <Popup trigger={<Button icon="filter" />} flowing hoverable>
          <DocTypeFilters value={[]} onChange={v => console.log('Nope', v)} />
        </Popup>
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
  }
}

export default enhance(AppSearchContent);
