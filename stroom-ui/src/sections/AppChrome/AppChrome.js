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
import { compose, withState } from 'recompose';
import { Button, Sidebar, Segment, Menu, Icon } from 'semantic-ui-react';

import { actionCreators, TAB_TYPES } from './redux';
import ContentTabs from './ContentTabs';

const { tabOpened } = actionCreators;
const withIsOpen = withState('isMenuOpen', 'setIsMenuOpen', false);

const enhance = compose(
  connect((state, props) => ({}), {
    tabOpened,
  }),
  withIsOpen,
);

const AppChrome = enhance(({ tabOpened, isMenuOpen, setIsMenuOpen }) => (
  <div className="app-chrome">
    <Sidebar.Pushable as={Segment}>
      <Sidebar
        as={Menu}
        animation="push"
        width="thin"
        visible={isMenuOpen}
        icon="labeled"
        vertical
        inverted
        color="blue"
      >
        <Menu.Item onClick={() => setIsMenuOpen(false)}>
          <Menu.Header content="Stroom" />
        </Menu.Item>
        <Menu.Item name="explorer" onClick={() => tabOpened(TAB_TYPES.EXPLORER_TREE)}>
          <Icon name="eye" />
          Explorer
        </Menu.Item>
        <Menu.Item name="user">
          <Icon name="user" />
          User
        </Menu.Item>
      </Sidebar>
      <Sidebar.Pusher>
        <Segment className="app-chrome__content">
          <ContentTabs />
        </Segment>
      </Sidebar.Pusher>
    </Sidebar.Pushable>
    <Button
      className="app-chrome__hamburger-menu-btn"
      color="blue"
      icon="bars"
      onClick={(e) => {
        e.target.blur(); // makes the button go back to normal colour, bit of a hack
        setIsMenuOpen(!isMenuOpen);
      }}
    />
  </div>
));

export default AppChrome;
