import React from 'react';
import PropTypes from 'prop-types';
import { compose } from 'recompose';
import { connect } from 'react-redux';
import { Button, Popup } from 'semantic-ui-react';

import { actionCreators } from './redux';

const { pipelineSettingsOpened } = actionCreators;

const enhance = compose(connect((state, props) => ({}), { pipelineSettingsOpened }));

const OpenPipelineSettings = ({ pipelineId, pipelineSettingsOpened }) => (
  <Popup
    trigger={
      <Button
        floated="right"
        circular
        icon="cogs"
        onClick={() => pipelineSettingsOpened(pipelineId)}
      />
    }
    content="Edit the settings for this pipeline"
  />
);

OpenPipelineSettings.propTypes = {
  pipelineId: PropTypes.string.isRequired,
};

export default enhance(OpenPipelineSettings);
