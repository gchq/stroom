import React from 'react';

import { compose, withState } from 'recompose';

import { Header, Icon } from 'semantic-ui-react';

import NewElement from './NewElement';
import { ElementCategories } from '../ElementCategories';

const withCategoryIsOpen = withState('isOpen', 'setIsOpen', true);

const ElementCategory = ({
  category, elements, isOpen, setIsOpen,
}) => (
  <div className="element-palette-category">
    <Header
      icon={`caret ${isOpen ? 'down' : 'right'}`}
      as="h3"
      onClick={() => setIsOpen(!isOpen)}
      className={isOpen ? 'open' : 'closed'}
      content={ElementCategories[category].displayName}
    />

    <div className={`element-palette-category__elements--${isOpen ? 'open' : 'closed'}`}>
      {elements.map(e => <NewElement key={e.type} element={e} />)}
    </div>
  </div>
);

export default compose(withCategoryIsOpen)(ElementCategory);
