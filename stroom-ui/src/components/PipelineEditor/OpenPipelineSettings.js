import React from 'react';
import PropTypes from 'prop-types';
import { Button } from 'semantic-ui-react';
import Tooltip from 'components/Tooltip';

const OpenPipelineSettings = ({ pipelineId, pipelineSettingsOpened }) => (
  <Tooltip
    trigger={
      <Button
        className="icon-button"
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
  pipelineSettingsOpened: PropTypes.func.isRequired,
};

export default OpenPipelineSettings;
