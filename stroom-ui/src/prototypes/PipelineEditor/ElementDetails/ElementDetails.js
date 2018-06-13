import React from 'react';
import PropTypes from 'prop-types';

import { compose } from 'recompose';
import { connect } from 'react-redux';

const ElementDetails = () => (
  <div className="element-details">
    <h1>Todo</h1>
  </div>
);

ElementDetails.propTypes = {};

export default compose(connect(
  state => ({
    // state
  }),
  {
    // actions
  },
))(ElementDetails);
