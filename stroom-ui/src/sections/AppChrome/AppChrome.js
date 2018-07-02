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
import { Button, Menu, Icon } from 'semantic-ui-react';

import { actionCreators, TAB_TYPES } from './redux';
import ContentTabs from './ContentTabs';

const { tabOpened } = actionCreators;
const withIsExpanded = withState('isExpanded', 'setIsExpanded', false);

const enhance = compose(
  connect((state, props) => ({}), {
    tabOpened,
  }),
  withIsExpanded,
);

const AppChrome = enhance(({ tabOpened, isExpanded, setIsExpanded }) => {
  const toggleExpanded = () => setIsExpanded(!isExpanded);

  const menu = isExpanded ? (
    <Menu vertical fluid color="blue" inverted>
      <Menu.Item onClick={toggleExpanded}>
        <Icon name="bars" />
        Stroom
      </Menu.Item>
      <Menu.Item name="explorer" onClick={() => tabOpened(TAB_TYPES.EXPLORER_TREE)}>
        <Icon name="eye" />
        Explorer
      </Menu.Item>
      <Menu.Item name="trackers" onClick={() => tabOpened(TAB_TYPES.TRACKER_DASHBOARD)}>
        <Icon name="tasks" />
        Trackers
      </Menu.Item>
      <Menu.Item name="user">
        <Icon name="user" />
        User
      </Menu.Item>
    </Menu>
  ) : (
    <Button.Group vertical color="blue" size="large">
      <Button icon="bars" onClick={toggleExpanded} />
      <Button icon="eye" onClick={() => tabOpened(TAB_TYPES.EXPLORER_TREE)} />
      <Button icon="tasks" onClick={() => tabOpened(TAB_TYPES.TRACKER_DASHBOARD)} />
      <Button icon="user" />
    </Button.Group>
  );

  return (
    <div className="app-chrome">
      <div className="app-chrome__sidebar">{menu}</div>
      <div className="app-chrome__content">
        <ContentTabs />
      </div>
    </div>
  );
});

export default AppChrome;
