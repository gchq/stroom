import React from 'react';

import NewElement from './NewElement';

const ElementCategory = ({ category, elements }) => (
  <div className="element-palette-category">
    <h3 className="element-palette-category__title">{category}</h3>
    <div className="element-palette-category__elements">
      {elements.map(e => <NewElement key={e.type} element={e} />)}
    </div>
  </div>
);

export default ElementCategory;
