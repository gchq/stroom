import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { lifecycle, compose } from 'recompose';
import Mousetrap from 'mousetrap';

import { actionCreators } from './redux';

const { keyDown, keyUp } = actionCreators;

const KeyIsDown = (filters = ['Control', 'Shift', 'Alt', 'Meta']) =>
  compose(
    connect((state, props) => ({}), { keyDown, keyUp }),
    lifecycle({
      componentDidMount() {
        const { keyDown, keyUp } = this.props;
        window.onkeydown = function (e) {
          if (filters.includes(e.key)) {
            keyDown(e.key);
          }
        };
        window.onkeyup = function (e) {
          if (filters.includes(e.key)) {
            keyUp(e.key);
          }
        };
      },
    }),
  );

export default KeyIsDown;
