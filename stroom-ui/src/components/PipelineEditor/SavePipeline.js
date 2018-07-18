import React from 'react';
import PropTypes from 'prop-types';
import { compose } from 'recompose';

import withPipeline from './withPipeline';
import ActionBarItem from 'sections/AppChrome/ActionBarItem';
import { savePipeline } from './pipelineResourceClient';

const enhance = compose(withPipeline({ savePipeline }));

const SavePipeline = ({ pipeline: { isSaving, isDirty }, savePipeline, pipelineId }) => (
  <ActionBarItem
    buttonProps={{ icon: 'save', color: isDirty ? 'blue' : undefined, loading: isSaving }}
    content={isDirty ? 'Save changes' : 'Changes saved'}
    onClick={() => {
      if (isDirty) savePipeline(pipelineId);
    }}
  />
);

SavePipeline.propTypes = {
  pipelineId: PropTypes.string.isRequired,
};

export default enhance(SavePipeline);
