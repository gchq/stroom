import React from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { Button } from 'semantic-ui-react';

import { savePipeline } from './pipelineResourceClient';

const enhance = connect(
  (state, props) => ({
    pipeline: state.pipelineEditor.pipelines[props.pipelineId],
    elements: state.pipelineEditor.elements,
  }),
  {
    // action, needed by lifecycle hook below
    savePipeline,
  },
);

const SavePipeline = ({ pipeline: { isSaving, isDirty }, savePipeline, pipelineId }) => (
  <div>
    <Button
      icon="save"
      loading={isSaving}
      disabled={!isDirty}
      color="blue"
      size="huge"
      circular
      onClick={() => savePipeline(pipelineId)}
    />
  </div>
);

SavePipeline.propTypes = {
  pipelineId: PropTypes.string.isRequired,
};

export default enhance(SavePipeline);
