import React from 'react';

import { Input } from 'semantic-ui-react';

const SingleValueWidget = ({ value, onChange, valueType }) => (
  <Input
    placeholder="value"
    type={valueType}
    value={value || ''}
    onChange={({ target: { value } }) => onChange(value)}
  />
);

export default SingleValueWidget;
