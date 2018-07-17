import React from 'react';
import PropTypes from 'prop-types';
import { compose } from 'recompose';
import { connect } from 'react-redux';

import ActionBarItem from 'components/ActionBarItem';
import { actionCreators } from './redux';

const { pipelineSettingsOpened } = actionCreators;

const enhance = compose(connect((state, props) => ({}), { pipelineSettingsOpened }));

const OpenPipelineSettings = ({ pipelineId, pipelineSettingsOpened }) => (
  <ActionBarItem
    icon="cogs"
    content="Edit the settings for this pipeline"
    onClick={() => pipelineSettingsOpened(pipelineId)}
  />
);

OpenPipelineSettings.propTypes = {
  pipelineId: PropTypes.string.isRequired,
};

export default enhance(OpenPipelineSettings);
