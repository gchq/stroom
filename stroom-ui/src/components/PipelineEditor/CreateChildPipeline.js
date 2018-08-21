import React from 'react';
import PropTypes from 'prop-types';
import { Button, Popup } from 'semantic-ui-react';

const CreateChildPipeline = ({ pipelineId, startInheritedPipeline }) => (
  <Popup
    trigger={
      <Button
        floated="right"
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
  startInheritedPipeline: PropTypes.func.isRequired
};

export default CreateChildPipeline;
