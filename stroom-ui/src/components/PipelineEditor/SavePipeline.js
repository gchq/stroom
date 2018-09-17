import React from 'react';
import PropTypes from 'prop-types';

import Button from 'components/Button';
import Tooltip from 'components/Tooltip';

const SavePipeline = ({ pipeline: { isSaving, isDirty }, savePipeline, pipelineId }) => (
  <Tooltip
    trigger={
      <Button
        circular
        icon="save"
        selected={isDirty}
        onClick={() => {
          if (isDirty) savePipeline(pipelineId);
        }}
      />
    }
    content={isDirty ? 'Save changes' : 'Changes saved'}
  />
);

SavePipeline.propTypes = {
  pipelineId: PropTypes.string.isRequired,
  pipeline: PropTypes.object.isRequired,
  savePipeline: PropTypes.func.isRequired,
};

export default SavePipeline;
