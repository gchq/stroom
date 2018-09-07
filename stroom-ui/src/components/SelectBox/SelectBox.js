import React from 'react';
import PropTypes from 'prop-types';

const SelectBox = ({ options, value, onChange }) => (
  <select value={value} onChange={({ target: { value } }) => onChange(value)}>
    {options.map(f => (
      <option key={f.value} value={f.value}>
        {f.text}
      </option>
    ))}
  </select>
);

SelectBox.propTypes = {
  options: PropTypes.arrayOf(PropTypes.shape({
    text: PropTypes.string.isRequired,
    value: PropTypes.string.isRequired,
  })),
  onChange: PropTypes.func.isRequired,
  value: PropTypes.string,
};

export default SelectBox;
