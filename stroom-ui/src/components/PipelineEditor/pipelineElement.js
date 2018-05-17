import React, { Component } from 'react';
import PropTypes from 'prop-types';

import { connect } from 'react-redux'

class PipelineEditor extends Component {
    static propTypes = {
        pipelineId : PropTypes.string.isRequired,
        elementId : PropTypes.string.isRequired
    }

    render() {
        return (
            <g>
                <text x="20" y="35">Pipeline Element {this.props.pipelineId} - {this.props.elementId}</text>
                <circle cx={100} cy={100} r={20} />
            </g>
        )
    }
}

export default connect(
    (state) => ({
        // state
    }),
    {
        // actions
    }
)(PipelineEditor)