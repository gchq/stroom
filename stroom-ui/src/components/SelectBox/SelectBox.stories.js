import React from 'react';
import { withState } from 'recompose';
import { storiesOf } from '@storybook/react';

import SelectBox from './SelectBox';

const enhanceControlled = withState('value', 'onChange', undefined);
let SelectBoxWrapped = ({ value, onChange, options }) => (
  <SelectBox value={value} onChange={onChange} options={options} />
);
SelectBoxWrapped = enhanceControlled(SelectBoxWrapped);

storiesOf('Select Box', module).add('Simple', () => (
  <SelectBoxWrapped
    options={[1, 2, 3].map(o => ({
      text: `Option ${o}`,
      value: `option${o}`,
    }))}
  />
));
