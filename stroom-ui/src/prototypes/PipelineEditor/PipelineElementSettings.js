import React, { Component } from 'react';
import PropTypes from 'prop-types';

import { compose } from 'redux';

import { withPipeline } from './withPipeline';
import { withSelectedPipelineElement } from './withSelectedPipelineElement';

class PipelineElementSettings extends Component {
    static propTypes = {
        pipelineId : PropTypes.string.isRequired,
        pipeline: PropTypes.object.isRequired,
        selectedElementId : PropTypes.string.isRequired
    }

    render() {
        let {
            pipelineId,
            pipeline,
            selectedElementId
        } = this.props;

        return (
            <div>Pipeline {pipelineId} - Element {selectedElementId}</div>
        )
    }
}

export default compose(
    withPipeline(),
    withSelectedPipelineElement()
)(PipelineElementSettings);