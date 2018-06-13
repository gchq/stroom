import React from 'react';
import PropTypes from 'prop-types';

import { compose } from 'recompose';
import { connect } from 'react-redux';

import { withPipeline } from '../withPipeline';

const ElementDetails = ({ pipelineId, pipeline, selectedElementId }) => (
  <div className="element-details">
    {selectedElementId ? <h3>{selectedElementId}</h3> : <h3> Please select a pipeline</h3>}
  </div>
);

ElementDetails.propTypes = {
  pipelineId: PropTypes.string.isRequired,
  pipeline: PropTypes.object.isRequired,
};

export default compose(
  connect(
    state => ({
      // state
    }),
    {
      // actions
    },
  ),
  withPipeline(),
)(ElementDetails);
