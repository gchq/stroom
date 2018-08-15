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

import { Table, Header, Icon } from 'semantic-ui-react';
import WithHeader from 'components/WithHeader';

const Welcome = props => (
  <WithHeader
    header={
      <Header as="h3">
        <Icon name="home" />
        <Header.Content>Welcome</Header.Content>
      </Header>
    }
    content={
      <div className="Welcome__container">
        <div className="Welcome">
          <h4>Global shortcut keys</h4>
          <Table definition className="Welcome__shortcutKeysTable">
            <Table.Header>
              <Table.Row>
                <Table.HeaderCell />
                <Table.HeaderCell>Shortcut</Table.HeaderCell>
              </Table.Row>
            </Table.Header>

            <Table.Body>
              <Table.Row>
                <Table.Cell>Document search</Table.Cell>
                <Table.Cell>
                  <code>ctrl + shift + f</code>
                </Table.Cell>
              </Table.Row>
              <Table.Row>
                <Table.Cell>Recent documents</Table.Cell>
                <Table.Cell>
                  <code> ctrl + shift + e</code>
                </Table.Cell>
              </Table.Row>
            </Table.Body>
          </Table>
        </div>
      </div>
    }
  />
);

export default Welcome;
