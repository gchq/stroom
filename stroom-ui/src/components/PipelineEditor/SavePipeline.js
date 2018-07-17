import React from 'react';
import PropTypes from 'prop-types';
import { compose } from 'recompose';

import withPipeline from './withPipeline';
import ActionBarItem from 'sections/AppChrome/ActionBarItem';
import { savePipeline } from './pipelineResourceClient';

const enhance = compose(withPipeline({ savePipeline }));

const SavePipeline = ({ pipeline: { isSaving, isDirty }, savePipeline, pipelineId }) => (
  <ActionBarItem
    icon="save"
    content={isDirty ? 'Save changes' : 'Changes saved'}
    color={isDirty ? 'blue' : undefined}
    onClick={() => {
      if (isDirty) savePipeline(pipelineId);
    }}
  />
);

SavePipeline.propTypes = {
  pipelineId: PropTypes.string.isRequired,
};

export default enhance(SavePipeline);
