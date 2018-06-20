import React from 'react';

import { compose } from 'recompose';
import { connect } from 'react-redux';

import ElementCategory from './ElementCategory';

const enhance = compose(connect(
  state => ({
    // state
    elementsByCategory: state.elements.byCategory || {},
  }),
  {
    // actions
  },
));

const ElementPalette = enhance(({ elementsByCategory }) => (
  <div className="element-palette">
    {Object.entries(elementsByCategory).map(k => (
      <ElementCategory key={k[0]} category={k[0]} elements={k[1]} />
    ))}
  </div>
));

export default ElementPalette;
