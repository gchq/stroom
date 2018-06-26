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

const SavePipeline = enhance(({ isDirty, pipelineId, savePipeline }) => (
  <React.Fragment>
    <Button
      circular
      disabled={!isDirty}
      color="blue"
      icon="save"
      size="huge"
      onClick={() => savePipeline(pipelineId)}
    />
    {isDirty ? '*' : undefined}
  </React.Fragment>
));

SavePipeline.propTypes = {
  pipelineId: PropTypes.string.isRequired,
};

export default SavePipeline;
