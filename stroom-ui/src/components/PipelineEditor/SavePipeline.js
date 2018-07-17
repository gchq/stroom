import React from 'react';
import PropTypes from 'prop-types';
import { compose, branch, renderNothing } from 'recompose';
import { connect } from 'react-redux';

import ActionBarItem from 'components/ActionBarItem';
import { savePipeline } from './pipelineResourceClient';

const enhance = compose(
  connect(
    ({ pipelineEditor: { pipelines, elements } }, { pipelineId }) => ({
      pipeline: pipelines[pipelineId],
      elements,
    }),
    {
      // action, needed by lifecycle hook below
      savePipeline,
    },
  ),
  branch(({ pipeline }) => !pipeline, renderNothing),
  branch(({ pipeline: { pipeline } }) => !pipeline, renderNothing),
);

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
