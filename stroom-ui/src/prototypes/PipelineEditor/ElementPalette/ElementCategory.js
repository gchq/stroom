import React from 'react';

import { compose, withState } from 'recompose';

import { Header, Icon, Accordion } from 'semantic-ui-react';

import NewElement from './NewElement';
import { ElementCategories } from '../ElementCategories';

const withCategoryIsOpen = withState('isOpen', 'setIsOpen', true);

const ElementCategory = ({
  category, elements, isOpen, setIsOpen,
}) => (
  <div className="element-palette-category">
    <Accordion styled>
      <Accordion.Title active={isOpen} onClick={() => setIsOpen(!isOpen)}>
        <Icon name="dropdown" /> {ElementCategories[category].displayName}
        {/* <Header
      icon={`caret ${isOpen ? 'down' : 'right'}`}
      as="h3"
      onClick={() => setIsOpen(!isOpen)}
      className={isOpen ? 'open' : 'closed'}
      content={ElementCategories[category].displayName}
    /> */}
      </Accordion.Title>
      <Accordion.Content active={isOpen}>
        <div className={`element-palette-category__elements--${isOpen ? 'open' : 'closed'}`}>
          {elements.map(e => <NewElement key={e.type} element={e} />)}
        </div>
      </Accordion.Content>
    </Accordion>
  </div>
);

export default compose(withCategoryIsOpen)(ElementCategory);
