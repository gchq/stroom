import React from 'react';
import PropTypes from 'prop-types';

import { connect } from 'react-redux';
import { Button } from 'semantic-ui-react';

import { savePipeline } from './pipelineResourceClient';

const enhance = connect((state, props) => ({}), {
  savePipeline,
});

const SavePipeline = enhance(({ pipelineId, savePipeline }) => (
  <Button circular basic icon="save outline" size="huge" onClick={() => savePipeline(pipelineId)} />
));

SavePipeline.propTypes = {
  pipelineId: PropTypes.string.isRequired,
};

export default SavePipeline;
