import React from 'react';
import PropTypes from 'prop-types';

import { connect } from 'react-redux';
import { Button } from 'semantic-ui-react';

import { savePipeline } from './pipelineResourceClient';

const enhance = connect(
  (state, props) => ({
    isDirty: state.pipelines[props.pipelineId].isDirty,
  }),
  {
    savePipeline,
  },
);

const SavePipeline = ({ isDirty, pipelineId, savePipeline }) => (
  <Button
    disabled={!isDirty}
    color="blue"
    icon="save"
    size="huge"
    onClick={() => savePipeline(pipelineId)}
  />
);

SavePipeline.propTypes = {
  pipelineId: PropTypes.string.isRequired,
};

export default enhance(SavePipeline);
