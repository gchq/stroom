import React from 'react';
import PropTypes from 'prop-types';

import DropdownSelect from 'components/DropdownSelect';
import permissionInheritanceValues from './permissionInheritanceValues';

const piOptions = Object.values(permissionInheritanceValues).map(pi => ({
  key: pi,
  value: pi,
  text: pi,
}));

const PermissionInheritancePicker = props => (
  <DropdownSelect {...props} placeholder="Permission Inheritance" options={piOptions} />
);

PermissionInheritancePicker.propTypes = {
  value: PropTypes.string,
  onChange: PropTypes.func.isRequired,
};

export default PermissionInheritancePicker;
