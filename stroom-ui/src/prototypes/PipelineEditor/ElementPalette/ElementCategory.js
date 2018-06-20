import React from 'react';
import PropTypes from 'prop-types';

import { compose, withState } from 'recompose';

import { Icon, Accordion } from 'semantic-ui-react';

import NewElement from './NewElement';
import { ElementCategories } from '../ElementCategories';

const withCategoryIsOpen = withState('isOpen', 'setIsOpen', true);

const enhance = compose(withCategoryIsOpen)

const ElementCategory = enhance(({
  category, elements, isOpen, setIsOpen,
}) => (
  <div className="element-palette-category">
    <Accordion styled>
      <Accordion.Title active={isOpen} onClick={() => setIsOpen(!isOpen)}>
        <Icon name="dropdown" /> {ElementCategories[category].displayName}
      </Accordion.Title>
      <Accordion.Content active={isOpen}>
        <div className={`element-palette-category__elements--${isOpen ? 'open' : 'closed'}`}>
          {elements.map(e => <NewElement key={e.type} element={e} />)}
        </div>
      </Accordion.Content>
    </Accordion>
  </div>
));

ElementCategory.propTypes = {
  category: PropTypes.string.isRequired,
  elements: PropTypes.array.isRequired
}

export default ElementCategory;
