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
import { compose } from 'recompose';
import { connect } from 'react-redux';
import { storiesOf, addDecorator } from '@storybook/react';
import { Checkbox, Header, Form } from 'semantic-ui-react';

import KeyIsDown from './KeyIsDown';
import { ReduxDecorator } from 'lib/storybook/ReduxDecorator';
import { KeyIsDownDecorator } from 'lib/storybook/KeyIsDownDecorator';

const TestComponent = ({ keyIsDown }) => (
  <div>
    <Header>Test Keys Down/Up</Header>
    <Form>
      <Form.Field>
        <Checkbox label="Control" checked={keyIsDown.Control} />
      </Form.Field>
      <Form.Field>
        <Checkbox label="Cmd/Meta" checked={keyIsDown.Meta} />
      </Form.Field>
      <Form.Field>
        <Checkbox label="Shift" checked={keyIsDown.Shift} />
      </Form.Field>
      <Form.Field>
        <Checkbox label="Alt" checked={keyIsDown.Alt} />
      </Form.Field>
    </Form>
  </div>
);

const TestComponentA = compose(connect(({ keyIsDown }) => ({ keyIsDown })), KeyIsDown())(TestComponent);
const TestComponentB = compose(connect(({ keyIsDown }) => ({ keyIsDown })), KeyIsDown(['Control']))(TestComponent);
const TestComponentC = connect(({ keyIsDown }) => ({ keyIsDown }))(TestComponent);

storiesOf('Key Is Down', module)
  .addDecorator(ReduxDecorator)
  .add('Test Component', () => <TestComponentA />)
  .add('Test Component (only detect Control)', () => <TestComponentB />);

storiesOf('Key Is Down (decorated, ctrl, alt)', module)
  .addDecorator(KeyIsDownDecorator(['Control', 'Alt']))
  .addDecorator(ReduxDecorator)
  .add('Test Component', () => <TestComponentC />);
