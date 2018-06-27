import React from 'react';
import PropTypes from 'prop-types';

import { compose, withState, withProps, branch, renderNothing } from 'recompose';

import { Icon, Accordion } from 'semantic-ui-react';

import NewElement from './NewElement';
import { ElementCategories } from '../ElementCategories';

const withCategoryIsOpen = withState('isOpen', 'setIsOpen', true);

const enhance = compose(
  withCategoryIsOpen,
  withProps(({ category }) => ({
    displayTitle: ElementCategories[category] ? ElementCategories[category].displayName : category,
  })),
  branch(({ elementsWithData }) => elementsWithData.length === 0, renderNothing),
);

const ElementCategory = enhance(({
  category, elementsWithData, isOpen, setIsOpen, displayTitle, icon,
}) => (
  <div className="element-palette-category">
    <Accordion styled>
      <Accordion.Title active={isOpen} onClick={() => setIsOpen(!isOpen)}>
        <Icon name="dropdown" /> {displayTitle}
      </Accordion.Title>
      <Accordion.Content active={isOpen}>
        <div className={`element-palette-category__elements--${isOpen ? 'open' : 'closed'}`}>
          {elementsWithData.map(e => <NewElement key={e.element.type} elementWithData={e} />)}
        </div>
      </Accordion.Content>
    </Accordion>
  </div>
));

ElementCategory.propTypes = {
  category: PropTypes.string.isRequired,
  elementsWithData: PropTypes.array.isRequired,
};

export default ElementCategory;
