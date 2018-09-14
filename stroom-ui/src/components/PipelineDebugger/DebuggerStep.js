import React from 'react';
import PropTypes from 'prop-types';
import { compose } from 'recompose';
import { connect } from 'react-redux';

const enhance = compose(
  connect(
    ({/* state desctructuring */ }) => ({/*mapStateToProps */ }),
    { /* mapDispatchToProps */ },
  ),
);

const DebuggerStep = ({ }) => (<div>TODO: debugger step information</div>);

DebuggerStep.propTypes = {};

export default enhance(DebuggerStep);