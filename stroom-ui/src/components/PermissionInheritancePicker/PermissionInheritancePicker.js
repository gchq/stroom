import React from 'react';
import PropTypes from 'prop-types';

import SelectBox from 'components/SelectBox';
import permissionInheritanceValues from './permissionInheritanceValues';

const piOptions = Object.values(permissionInheritanceValues).map(pi => ({
  value: pi,
  text: pi,
}));

const PermissionInheritancePicker = props => (
  <SelectBox {...props} placeholder="Permission Inheritance" options={piOptions} />
);

PermissionInheritancePicker.propTypes = {
  value: PropTypes.string,
  onChange: PropTypes.func.isRequired,
};

export default PermissionInheritancePicker;
