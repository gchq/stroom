import React from 'react';
import PropTypes from 'prop-types';
import { Button, Popup } from 'semantic-ui-react';

const SavePipeline = ({ pipeline: { isSaving, isDirty }, savePipeline, pipelineId }) => (
  <Popup
    trigger={
      <Button
        floated="right"
        circular
        icon="save"
        color={isDirty ? 'blue' : undefined}
        loading={isSaving}
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
