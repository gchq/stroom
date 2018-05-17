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

import { ItemTypes } from './dragDropTypes';
import { DragSource } from 'react-dnd';

import { Icon } from 'semantic-ui-react'

import {
    selectDocRef,
    openDocRef,
    openDocRefContextMenu
} from './redux/explorerTreeReducer';

import DocRefMenu from './docRefMenu';

const dragSource = {
	canDrag(props) {
        let explorerState = props.explorers[props.explorerId];
		return explorerState.allowDragAndDrop;
	},
    beginDrag(props) {
        return {
            ...props.docRef
        };
    }
};

function dragCollect(connect, monitor) {
    return {
        connectDragSource: connect.dragSource(),
        isDragging: monitor.isDragging()
    }
}

class DocRef extends Component {
    static propTypes = {
        // Props
        explorerId : PropTypes.string.isRequired,
        docRef : PropTypes.object.isRequired,

        // State
        explorers : PropTypes.object.isRequired,

        // Actions
        selectDocRef : PropTypes.func.isRequired,
        openDocRef : PropTypes.func.isRequired,
        openDocRefContextMenu : PropTypes.func.isRequired,

        // React DnD
        connectDragSource: PropTypes.func.isRequired,
        isDragging: PropTypes.bool.isRequired
    }
    
    // these are required to tell the difference between single/double clicks
    timer = 0;
    delay = 200;
    prevent = false;

    state = {
        isReady : false,
        isSelected : false,
        isContextMenuOpen : false
    }

    static getDerivedStateFromProps(nextProps, prevState) {
        let explorer = nextProps.explorers[nextProps.explorerId];

        if (!!explorer) {
            return {
                isReady : true,
                isSelected : explorer.isSelected[nextProps.docRef.uuid],
                isContextMenuOpen : !!explorer.contextMenuItemUuid && explorer.contextMenuItemUuid === nextProps.docRef.uuid
            }
        } else {
            return {
                isReady : false
            }
        }
    }

    onSingleClick() {
        this.timer = setTimeout(function() {
            if (!this.prevent) {
                this.props.selectDocRef(this.props.explorerId, this.props.docRef); 
            }
            this.prevent = false;
        }.bind(this), this.delay);
    }

    onDoubleClick() {
        clearTimeout(this.timer);
        this.prevent = true;
        this.props.openDocRef(this.props.explorerId, this.props.docRef)
    }

    onRightClick(e) {
        this.props.openDocRefContextMenu(this.props.explorerId, this.props.docRef);
        e.preventDefault();
    }

    render() {
        if (!this.state.isReady) {
            return (<div>Awaiting explorer state</div>)
        }

        const { connectDragSource, isDragging } = this.props;

        let className = ''
        if (isDragging) {
            className += ' doc-ref__dragging'
        }
        if (this.state.isSelected) {
            className += ' doc-ref__selected'
        }
        if (this.state.isContextMenuOpen) {
            className += ' doc-ref__context-menu-open'
        }

        return connectDragSource(
            <div className={className}
                onContextMenu={this.onRightClick.bind(this)}
                onDoubleClick={this.onDoubleClick.bind(this)}
                onClick={this.onSingleClick.bind(this)}>
                <DocRefMenu 
                    explorerId={this.props.explorerId}
                    docRef={this.props.docRef}
                    isOpen={this.state.isContextMenuOpen}
                />
                <span>
                    <Icon name='file outline'/>
                    {this.props.docRef.name}
                </span>
            </div>
        )
    }
}

export default connect(
    (state) => ({
        explorers : state.explorerTree.explorers
    }),
    {
        selectDocRef,
        openDocRef,
        openDocRefContextMenu
    }
)
    (DragSource(ItemTypes.DOC_REF, dragSource, dragCollect)(DocRef));