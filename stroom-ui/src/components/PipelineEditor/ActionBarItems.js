import React from 'react';
import PropTypes from 'prop-types';

import SavePipeline from './SavePipeline';
import CreateChildPipeline from './CreateChildPipeline';
import OpenPipelineSettings from './OpenPipelineSettings';

const ActionBarItems = ({ pipelineId }) => (
  <React.Fragment>
    <SavePipeline pipelineId={pipelineId} />
    <CreateChildPipeline pipelineId={pipelineId} />
    <OpenPipelineSettings pipelineId={pipelineId} />
  </React.Fragment>
);

ActionBarItems.propTypes = {
  pipelineId: PropTypes.string.isRequired
}

export default ActionBarItems;