import React from 'react';
import PropTypes from 'prop-types';

import { compose } from 'recompose';
import { connect } from 'react-redux';

import { Select } from 'semantic-ui-react';

import { actionCreators } from './redux';
import permissionInheritanceValues from './permissionInheritanceValues';

const { permissionInheritancePicked } = actionCreators;

const piOptions = Object.values(permissionInheritanceValues).map(pi => ({
  key: pi,
  value: pi,
  text: pi,
}));

const enhance = compose(connect(
  (state, props) => ({
    permissionInheritance: state.permissionInheritancePicker[props.pickerId],
  }),
  { permissionInheritancePicked },
));

const PermissionInheritancePicker = ({
  pickerId,
  permissionInheritance,
  permissionInheritancePicked,
}) => (
  <Select
    placeholder="Permission Inheritance"
    onChange={(e, { value }) => permissionInheritancePicked(pickerId, value)}
    options={piOptions}
    value={permissionInheritance}
  />
);

PermissionInheritancePicker.propTypes = {
  pickerId: PropTypes.string.isRequired,
};

export default enhance(PermissionInheritancePicker);
