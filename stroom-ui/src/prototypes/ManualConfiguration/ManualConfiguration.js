import React from 'react';

import { compose } from 'recompose';
import { connect } from 'react-redux';

import { actionCreators as configActionCreators } from 'startup/config';

const { updateConfig } = configActionCreators;

const enhance = compose(connect((state, props) => ({}), {
  updateConfig,
}));

const ManualConfiguration = enhance(({ updateConfig }) => <div>Manual Configuration</div>);

export default ManualConfiguration;
