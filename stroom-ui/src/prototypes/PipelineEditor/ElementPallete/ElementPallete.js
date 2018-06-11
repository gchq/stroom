import React from 'react';

import { connect } from 'react-redux';

import { Header } from 'semantic-ui-react';

import ElementCategory from './ElementCategory';

const ElementPallete = ({ elementsByCategory }) => (
  <div className="element-pallete">
    <Header as="h2">Element Pallete</Header>

    {Object.entries(elementsByCategory).map(k => (
      <ElementCategory key={k[0]} category={k[0]} elements={k[1]} />
    ))}
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
