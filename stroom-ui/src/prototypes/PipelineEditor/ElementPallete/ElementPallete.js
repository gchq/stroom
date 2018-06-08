import React from 'react';

import { connect } from 'react-redux';

import { Header } from 'semantic-ui-react';

import ElementCategory from './ElementCategory';

const ElementPallete = ({ elementsByCategory }) => (
  <div className="element-pallete">
    <h2 className="element-pallete__title">Elements</h2>

    <div className="element-pallete__categories">
      {Object.entries(elementsByCategory).map(k => (
        <ElementCategory key={k[0]} category={k[0]} elements={k[1]} />
      ))}
    </div>
  </div>
);

export default connect(
  state => ({
    // state
    elementsByCategory: state.elements.byCategory || {},
  }),
  {
    // actions
  },
)(ElementPallete);
