import React, { Component } from 'react';
import PropTypes from 'prop-types';

import { connect } from 'react-redux'

import { Input, Icon } from 'semantic-ui-react'

import './docExplorer.css'

import Folder from './folder';

import {
    searchTermChanged,
    explorerTreeOpened,
    DEFAULT_EXPLORER_ID
} from './redux/explorerTreeReducer';

class DocExplorer extends Component {
    static propTypes = {
        explorerId: PropTypes.string.isRequired,
        documentTree: PropTypes.object.isRequired,
        explorers: PropTypes.object.isRequired,
        allowMultiSelect : PropTypes.bool.isRequired,
        allowDragAndDrop : PropTypes.bool.isRequired,
        typeFilter : PropTypes.string,
    
        searchTermChanged: PropTypes.func.isRequired,
        explorerTreeOpened: PropTypes.func.isRequired
    }

    static defaultProps = {
        explorerId : DEFAULT_EXPLORER_ID,
        allowMultiSelect : true,
        allowDragAndDrop : true,
        typeFilter : undefined
    }

    componentDidMount() {
        // We give these properties to the explorer state, then the nested objects can read these values from
        // redux using the explorerId which is passed all the way down.
        this.props.explorerTreeOpened(this.props.explorerId, 
            this.props.allowMultiSelect, 
            this.props.allowDragAndDrop,
            this.props.typeFilter);
    }

    render() {
        let { documentTree, searchTermChanged } = this.props;
        let explorerState = this.props.explorers[this.props.explorerId];

        if (!explorerState) {
            return (<div>Awaiting explorer state</div>)
        }

        return (
            <div>
                <Input icon='search'
                    placeholder='Search...'
                    value={explorerState.searchTerm}
                    onChange={e => searchTermChanged(this.props.explorerId, e.target.value)}
                />
                <Folder explorerId={this.props.explorerId} folder={documentTree} />
            </div>
        )
    }
}

export default connect(
    (state) => ({
        documentTree: state.explorerTree.documentTree,
        explorers: state.explorerTree.explorers
    }),
    {
        searchTermChanged,
        explorerTreeOpened
    }
)(DocExplorer);

