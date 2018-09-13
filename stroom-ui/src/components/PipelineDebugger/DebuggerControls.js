import React from 'react';
import PropTypes from 'prop-types';
import { compose, lifecycle } from 'recompose';
import { connect } from 'react-redux';

import Button from 'components/Button';

// TODO:
//  - dispatch actions

const enhance = compose(
  connect(
    ({/* state desctructuring */ }) => ({/*mapStateToProps */ }),
    {},
  ),
);

const DebuggerControls = ({ }) => (
  <div>
    <Button icon='chevron-left' text='Previous' onClick={() => console.log('TODO: previous clicked')} />
    <Button icon='chevron-right' text='Next' onClick={() => console.log('TODO: next clicked')} />
  </div>
);

DebuggerControls.propTypes = {
  debuggerId: PropTypes.string.isRequired,
};

export default enhance(DebuggerControls);