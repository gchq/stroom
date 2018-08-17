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
import { Header, Icon, Dropdown, Grid } from 'semantic-ui-react';

import { actionCreators } from './redux';
import WithHeader from 'components/WithHeader';

const { themeChanged } = actionCreators;

const themeOptions = [
  {
    text: 'Light',
    value: 'theme-light',
  },
  {
    text: 'Dark',
    value: 'theme-dark',
  },
];
const enhance = compose(connect(
  (state, props) => ({
    theme: state.userSettings.theme,
  }),
  { themeChanged },
));

const RawUserSettings = ({ theme, themeChanged }) => (
  <div className="UserSettings__container">
    <h3>User Settings</h3>
    <Grid>
      <Grid.Column width={6}>Theme:</Grid.Column>
      <Grid.Column width={10}>
        <Dropdown
          fluid
          selection
          options={themeOptions}
          value={theme}
          onChange={(_, data) => {
            themeChanged(data.value);
          }}
        />
      </Grid.Column>
    </Grid>
  </div>
);

const RawWithHeader = props => (
  <WithHeader
    header={
      <Header as="h3">
        <Icon name="user" />
        <Header.Content>Me</Header.Content>
      </Header>
    }
    content={<RawUserSettings {...props} />}
  />
);

const UserSettings = enhance(RawWithHeader);

export default UserSettings;
