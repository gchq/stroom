import React from 'react';
import PropTypes from 'prop-types';

import { compose } from 'recompose';
import { connect } from 'react-redux';

import { Select } from 'semantic-ui-react';

import PermissionInheritance from './PermissionInheritance';

const piOptions = Object.values(PermissionInheritance).map(pi => ({
  key: pi,
  value: pi,
  text: pi,
}));

const enhance = compose(connect(
  (state, props) => ({
    pickedPermissionInheritance: state.explorerTree.pickedPermissionInheritance,
  }),
  {},
));

const PermissionInheritancePicker = ({ pickedPermissionInheritance }) => (
  <Select placeholder="Permission Inheritance" options={piOptions} />
);

export default enhance(PermissionInheritancePicker);
