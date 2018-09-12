import React from 'react';
import PropTypes from 'prop-types';

import Tooltip from 'components/Tooltip';
import Button from 'components/Button';

const CreateChildPipeline = ({ pipelineId, startInheritedPipeline }) => (
  <Tooltip
    trigger={
      <Button
        circular
        icon="recycle"
        onClick={() => startInheritedPipeline(pipelineId)}
      />
    }
    content="Create a child pipeline, using this one as a parent"
  />
);

CreateChildPipeline.propTypes = {
  pipelineId: PropTypes.string.isRequired,
  startInheritedPipeline: PropTypes.func.isRequired,
};

export default CreateChildPipeline;
