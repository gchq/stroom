import React from 'react';

const SingleValueWidget = ({ value, onChange, valueType }) => (
  <input
    placeholder="value"
    type={valueType}
    value={value || ''}
    onChange={({ target: { value } }) => onChange(value)}
  />
);

export default SingleValueWidget;
