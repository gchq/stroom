import React from 'react';
import PropTypes from 'prop-types';

import { compose } from 'recompose';
import { connect } from 'react-redux';

import ElementCategory from './ElementCategory';

const ElementPalette = ({ elementsByCategory }) => (
  <div className="element-palette">
    {Object.entries(elementsByCategory).map(k => (
      <ElementCategory key={k[0]} category={k[0]} elements={k[1]} />
    ))}
  </div>
);

ElementPalette.propTypes = {
  elementsByCategory: PropTypes.object.isRequired,
};

export default compose(connect(
  state => ({
    // state
    elementsByCategory: state.elements.byCategory || {},
  }),
  {
    // actions
  },
))(ElementPalette);
