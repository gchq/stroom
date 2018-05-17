import React, { Component } from 'react';
import PropTypes from 'prop-types';

import { connect } from 'react-redux'

import PipelineElement from './pipelineElement';

class PipelineEditor extends Component {
    static propTypes = {
        pipelineId : PropTypes.string.isRequired
    }

    render() {
        return (
            <div className='pipeline-editor'>
                <svg>
                    <text x="20" y="35">Pipeline Editor {this.props.pipelineId}</text>
                    <PipelineElement 
                        pipelineId={this.props.pipelineId}
                        elementId='test1'
                        />
                </svg>
                <div>
                    Pipeline Element Settings
                </div>
            </div>
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