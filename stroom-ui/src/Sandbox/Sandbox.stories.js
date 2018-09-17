import React from 'react';
import { compose, withStateHandlers } from 'recompose';
import { storiesOf } from '@storybook/react';

import { PollyDecorator } from 'lib/storybook/PollyDecorator';
import PanelGroup from 'react-panelgroup';
import loremIpsum from 'lorem-ipsum';

import { Basic, ExpandToFillManual, ExpandToFillFlexbox } from './Sandbox';

import 'styles/main.css';

const enhanceForm = withStateHandlers(({ name = '', age = 10 }) => ({ name, age }), {
  setName: ({}) => ({ target: { value } }) => ({ name: value }),
  setAge: ({}) => ({ target: { value } }) => ({ age: value }),
});

let TestForm = ({
  name, age, setName, setAge,
}) => (
  <form>
    <div>
      <label>Name</label>
      <input type="text" value={name} onChange={setName} />
    </div>
    <div>
      <label>Age</label>
      <input type="number" min="0" max="100" value={age} onChange={setAge} />
    </div>
    <button>Submit</button>
  </form>
);

TestForm = enhanceForm(TestForm);

storiesOf('Developer sandbox', module)
  .add('Basic', () => <Basic />)
  .add('Panel group 1', () => (
    <PanelGroup direction="column">
      <div>stuff1</div>
      <div>{loremIpsum({ count: 9999, units: 'words' })}</div>
    </PanelGroup>
  ))
  .add('Expand To Fill Manual', () => <ExpandToFillManual />)
  .add('Expand To Fill Flexbox', () => <ExpandToFillFlexbox />)
  .add('Test Form', () => <TestForm />);
