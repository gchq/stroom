import React from 'react';
import PropTypes from 'prop-types';
import { Button } from 'semantic-ui-react';
import Tooltip from 'components/Tooltip';

const SavePipeline = ({ pipeline: { isSaving, isDirty }, savePipeline, pipelineId }) => (
  <Tooltip
    trigger={
      <Button
        className="icon-button"
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
