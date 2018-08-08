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
import { Menu, Header, Icon } from 'semantic-ui-react';

import Mousetrap from 'mousetrap';

import WithHeader from 'components/WithHeader';
import { actionCreators as recentItemsActionCreators } from './redux';
import openDocRef from './openDocRef';

const {
  recentItemsClosed,
  recentItemsSelectionUp,
  recentItemsSelectionDown,
} = recentItemsActionCreators;

const upKeys = ['k', 'ctrl+k', 'up'];
const downKeys = ['j', 'ctrl+j', 'down'];
const openKeys = ['enter'];

const enhance = compose(
  withRouter,
  connect(
    ({ recentItems: { openItemStack, selectedItem, selectedDocRef } }, props) => ({
      openItemStack,
      selectedItem,
      selectedDocRef,
    }),
    {
      recentItemsClosed,
      openDocRef,
      recentItemsSelectionUp,
      recentItemsSelectionDown,
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
        openItemStack,
      } = this.props;

      Mousetrap.bind(upKeys, () => recentItemsSelectionUp());
      Mousetrap.bind(downKeys, () => recentItemsSelectionDown());
      Mousetrap.bind(openKeys, () => {
        if (this.props.selectedDocRef !== undefined) {
          openDocRef(history, this.props.selectedDocRef);
          recentItemsClosed();
        } else if (openItemStack.length > 0) {
          openDocRef(history, openItemStack[0]);
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

const RawRecentItems = ({
  history,
  recentItemsClosed,
  openItemStack,
  openDocRef,
  selectedItem,
  selectedDocRef,
  recentItemsSelectionUp,
  recentItemsSelectionDown,
}) => (
  <Menu vertical fluid>
    <div>
      {openItemStack.map((docRef, i) => {
        const title = docRef.name;
        return (
          <Menu.Item
            active={selectedItem === i}
            key={docRef.uuid}
            name={title}
            onClick={() => {
              openDocRef(history, docRef);
              recentItemsClosed();
            }}
          >
            {title}
          </Menu.Item>
        );
      })}
    </div>
  </Menu>
);

const RawWithHeader = props => (
  <WithHeader
    header={
      <Header as="h3">
        <Icon color="grey" name="file outline" />
        <Header.Content>Recent Items</Header.Content>
      </Header>
    }
    content={<RawRecentItems {...props} />}
  />
);

const RecentItems = enhance(RawWithHeader);

export default RecentItems;
