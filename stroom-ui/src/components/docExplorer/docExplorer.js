/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
} from './redux';

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

    state = {
        explorer : undefined
    }

    static getDerivedStateFromProps(nextProps, prevState) {
        let explorer = nextProps.explorers[nextProps.explorerId];
        let searchTerm = (!!explorer) ? explorer.searchTerm : '';
        return {
            explorer,
            searchTerm
        }
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

        if (!this.state.explorer) {
            return (<div>Awaiting explorer state</div>)
        }

        return (
            <div>
                <Input icon='search'
                    placeholder='Search...'
                    value={this.state.searchTerm}
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

