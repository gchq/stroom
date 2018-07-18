import React from 'react';
import PropTypes from 'prop-types';
import { compose } from 'recompose';
import { connect } from 'react-redux';

import ActionBarItem from 'sections/AppChrome/ActionBarItem';
import { actionCreators } from './redux';

const { startInheritedPipeline } = actionCreators;

const enhance = compose(connect((state, props) => ({}), { startInheritedPipeline }));

const CreateChildPipeline = ({ pipelineId, startInheritedPipeline }) => (
  <ActionBarItem
    buttonProps={{ icon: 'recycle' }}
    content="Create a child pipeline, using this one as a parent"
    onClick={() => startInheritedPipeline(pipelineId)}
  />
);

CreateChildPipeline.propTypes = {
  pipelineId: PropTypes.string.isRequired,
};

export default enhance(CreateChildPipeline);
