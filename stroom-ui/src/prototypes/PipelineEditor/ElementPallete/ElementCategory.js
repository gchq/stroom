import React from 'react';

import NewElement from './NewElement';

import { Header } from 'semantic-ui-react';

const ElementCategory = ({ category, elements }) => (
  <div className="element-pallete__category">
    <Header as="h3">{category}</Header>
    <div className="element-pallete__category__elements">
      {elements.map(e => <NewElement key={e.type} element={e} />)}
    </div>
  </div>
);

export default ElementCategory;
