import React from 'react';
import PropTypes from 'prop-types';
import { Button, Popup } from 'semantic-ui-react';

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
  pipelineSettingsOpened: PropTypes.func.isRequired
};

export default OpenPipelineSettings;
