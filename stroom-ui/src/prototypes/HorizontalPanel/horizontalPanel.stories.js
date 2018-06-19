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
import loremIpsum from 'lorem-ipsum';
import { Segment, Menu, Input, Checkbox, Button, Container } from 'semantic-ui-react';
import { storiesOf } from '@storybook/react';
import HorizontalPanel from './HorizontalPanel';

storiesOf('HorizontalPanel', module)
  .add('basic - segment content', () => (
    <HorizontalPanel
      title="Some title"
      onClose={() => console.log('closed')}
      content={<Segment>{loremIpsum({ count: 100, units: 'words' })}</Segment>}
      headerMenuItems={[
        <Menu.Item key="enabledCheckbox">
          <Checkbox toggle>thingy</Checkbox>
        </Menu.Item>,
      ]}
    />
  ))
  .add('basic - container content', () => (
    <HorizontalPanel
      title="Some title"
      onClose={() => console.log('closed')}
      content={<Container>{loremIpsum({ count: 100, units: 'words' })}</Container>}
      headerMenuItems={[
        <Menu.Item key="enabledCheckbox">
          <Checkbox toggle>thingy</Checkbox>
        </Menu.Item>,
      ]}
    />
  ))
  .add('basic - div content', () => (
    <HorizontalPanel
      title="Some title"
      onClose={() => console.log('closed')}
      content={<div>{loremIpsum({ count: 100, units: 'words' })}</div>}
      headerMenuItems={[
        <Menu.Item key="enabledCheckbox">
          <Checkbox toggle>thingy</Checkbox>
        </Menu.Item>,
      ]}
    />
  ));
