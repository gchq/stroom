import React from 'react';
import { connect } from 'react-redux';
import { withRouter } from 'react-router-dom';
import { compose, withProps } from 'recompose';

import openDocRef from './openDocRef';

export default compose(
  withRouter,
  connect(undefined, { openDocRef }),
  withProps(({ openDocRef, history }) => ({
    openDocRef: d => openDocRef(history, d),
  })),
);
