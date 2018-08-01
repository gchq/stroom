import React from 'react';
import PropTypes from 'prop-types';

import { Select } from 'semantic-ui-react';

import permissionInheritanceValues from './permissionInheritanceValues';

const piOptions = Object.values(permissionInheritanceValues).map(pi => ({
  key: pi,
  value: pi,
  text: pi,
}));

const PermissionInheritancePicker = ({ value, onChange }) => (
  <Select
    placeholder="Permission Inheritance"
    onChange={(e, { value }) => onChange(value)}
    options={piOptions}
    value={value}
  />
);

PermissionInheritancePicker.propTypes = {
  value: PropTypes.string,
  onChange: PropTypes.func.isRequired,
};

PermissionInheritancePicker.defaultProps = {
  onChange: v => console.log('Not implemented onChange, value ignored', v),
};

export default PermissionInheritancePicker;
