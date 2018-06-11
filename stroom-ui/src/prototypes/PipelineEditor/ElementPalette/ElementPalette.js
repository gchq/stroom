import React from 'react';
import PropTypes from 'prop-types';

import { compose, withState } from 'recompose';
import { connect } from 'react-redux';

import { Header, Button, Transition } from 'semantic-ui-react';

import ElementCategory from './ElementCategory';

const withIsOpen = withState('isOpen', 'setIsOpen', false);

const ElementPalette = ({ elementsByCategory, isOpen, setIsOpen }) => (
  <div className="element-palette">
    <Button onClick={() => setIsOpen(!isOpen)}>Add New Element</Button>
    <Transition animation="fade right" visible={isOpen} duration={500}>
      <div>
        <h2 className="element-palette__title">Elements</h2>

        <div className="element-palette__categories">
          {Object.entries(elementsByCategory).map(k => (
            <ElementCategory key={k[0]} category={k[0]} elements={k[1]} />
          ))}
        </div>
      </div>
    </Transition>
  </div>
);

ElementPalette.propTypes = {
  elementsByCategory: PropTypes.object.isRequired,
  isOpen: PropTypes.bool.isRequired,
  setIsOpen: PropTypes.func.isRequired,
};

export default compose(
  connect(
    state => ({
      // state
      elementsByCategory: state.elements.byCategory || {},
    }),
    {
      // actions
    },
  ),
  withIsOpen,
)(ElementPalette);
