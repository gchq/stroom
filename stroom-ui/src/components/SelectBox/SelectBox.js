import React from 'react';
import PropTypes from 'prop-types';

const SelectBox = ({
  options, value, onChange, placeholder, ...rest
}) => (
  <span className="styled-select">
    <select {...rest} value={value} onChange={({ target: { value } }) => onChange(value)}>
      <option value="" disabled selected>
        {placeholder}
      </option>
      {options.map(f => (
        <option key={f.value} value={f.value}>
          {f.text}
        </option>
      ))}
    </select>
  </span>
);

SelectBox.propTypes = {
  placeholder: PropTypes.string.isRequired,
  options: PropTypes.arrayOf(PropTypes.shape({
    text: PropTypes.string.isRequired,
    value: PropTypes.string.isRequired,
  })),
  onChange: PropTypes.func.isRequired,
  value: PropTypes.string,
};

SelectBox.defaultProps = {
  placeholder: 'Select an option',
};

export default SelectBox;
