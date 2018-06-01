import React from 'react';
import PropTypes from 'prop-types';

import { compose } from 'redux';

import { withPipeline } from './withPipeline';

const PipelineElementSettings = ({ pipelineId, pipeline, selectedElementId }) => (
  <div>
      Pipeline {pipelineId} - Element {selectedElementId}
  </div>
);

PipelineElementSettings.propTypes = {
  pipelineId: PropTypes.string.isRequired,
  pipeline: PropTypes.object.isRequired,
};

export default compose(withPipeline())(PipelineElementSettings);
