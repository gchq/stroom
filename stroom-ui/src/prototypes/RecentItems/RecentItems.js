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
import { compose, lifecycle } from 'recompose';
import { connect } from 'react-redux';
import { withRouter } from 'react-router-dom';
import { Header, Icon, Grid, Input } from 'semantic-ui-react';

import Mousetrap from 'mousetrap';

import DocRefListing from 'components/DocRefListing';
import { actionCreators as recentItemsActionCreators } from './redux';
import openDocRef from './openDocRef';

const {
  recentItemsClosed,
  recentItemsSelectionUp,
  recentItemsSelectionDown,
  filterTermUpdated,
} = recentItemsActionCreators;

const upKeys = ['k', 'ctrl+k', 'up'];
const downKeys = ['j', 'ctrl+j', 'down'];
const openKeys = ['enter'];

const enhance = compose(
  withRouter,
  connect(
    ({
      recentItems: {
        filteredItemStack, selectedItem, selectedDocRef, filterTerm,
      },
    }, props) => ({
      filteredItemStack,
      selectedItem,
      selectedDocRef,
      filterTerm,
    }),
    {
      recentItemsClosed,
      openDocRef,
      recentItemsSelectionUp,
      recentItemsSelectionDown,
      filterTermUpdated,
    },
  ),
  lifecycle({
    componentDidMount() {
      const {
        recentItemsSelectionUp,
        recentItemsSelectionDown,
        openDocRef,
        recentItemsClosed,
        history,
        filteredItemStack,
      } = this.props;

      Mousetrap.bind(upKeys, () => recentItemsSelectionUp());
      Mousetrap.bind(downKeys, () => recentItemsSelectionDown());
      Mousetrap.bind(openKeys, () => {
        if (this.props.selectedDocRef !== undefined) {
          openDocRef(history, this.props.selectedDocRef);
          recentItemsClosed();
        } else if (filteredItemStack.length > 0) {
          openDocRef(history, filteredItemStack[0]);
          recentItemsClosed();
        }
      });
    },
    componentWillUnmount() {
      Mousetrap.unbind(upKeys);
      Mousetrap.unbind(downKeys);
      Mousetrap.unbind(openKeys);
    },
  }),
);

const RecentItems = ({
  history,
  recentItemsClosed,
  filteredItemStack,
  selectedItem,
  selectedDocRef,
  recentItemsSelectionUp,
  recentItemsSelectionDown,
  filterTermUpdated,
  filterTerm,
}) => (
  <React.Fragment>
    <Grid className="content-tabs__grid">
      <Grid.Column width={4}>
        <Header as="h3">
          <Icon color="grey" name="file outline" />
          <Header.Content>Recent Items</Header.Content>
        </Header>
      </Grid.Column>

      <Grid.Column width={8}>
        <Input
          id="AppSearch__search-input"
          icon="search"
          placeholder="Search..."
          value={filterTerm}
          onChange={e => filterTermUpdated(e.target.value)}
          autoFocus
        />
      </Grid.Column>
    </Grid>
    <DocRefListing docRefs={filteredItemStack} selectedItem={selectedItem} />
  </React.Fragment>
);

export default enhance(RecentItems);
