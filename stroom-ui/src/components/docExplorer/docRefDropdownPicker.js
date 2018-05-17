import React, { Component } from 'react';
import PropTypes from 'prop-types';

import { connect } from 'react-redux'

import {
    Button,
    Header,
    Icon,
    Dropdown,
    Breadcrumb
} from 'semantic-ui-react'

import DocExplorer from './docExplorer';

import { iterateNodes, findItem } from 'lib/treeUtils';

class DocRefDropdownPicker extends Component {
    static propTypes = {
        pickerId : PropTypes.string.isRequired,
        documentTree : PropTypes.object.isRequired,

        typeFilter : PropTypes.string,
        value : PropTypes.object,
        onChange : PropTypes.func.isRequired
    }

    onDocRefSelected(event, data) {
        let picked = findItem(this.props.documentTree, data.value);
        this.props.onChange(picked);
    }

    render() {
        let value = (!!this.props.value) ? this.props.value.uuid : '';
        
        let options = [];
        iterateNodes(this.props.documentTree, (lineage, node) => {
            // If we are filtering on type, check this now
            if (!!this.props.typeFilter && (this.props.typeFilter !== node.type)) {
                return; // just skip out
            }

            // Compose the data that provides the breadcrumb to this node
            let sections = lineage.map(l => {
                return {
                    key: l.name, 
                    content: l.name,
                    link: true
                }
            });

            // Don't include folders as pickable items
            if (!node.children && node.uuid) {
                options.push({
                    key: node.uuid,
                    text: node.name,
                    value: node.uuid,
                    content : (
                        <div style={{width: '50rem'}}>
                            <Breadcrumb size='mini' icon='right angle' sections={sections} />
                            <div className='doc-ref-dropdown__item-name'>{node.name}</div>
                        </div>
                    )
                })
            }
        })
        
        return (
            <Dropdown
                selection
                search
                options={options}
                value={value}
                onChange={this.onDocRefSelected.bind(this)}
                placeholder='Choose an option'
                />
        )
    }
}

export default connect(
    (state) => ({
        documentTree : state.explorerTree.documentTree
    }),
    {
        // actions
    }
)(DocRefDropdownPicker);