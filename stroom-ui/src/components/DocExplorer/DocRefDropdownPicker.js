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

import { compose } from 'redux';
import { connect } from 'react-redux'

import {
    Button,
    Header,
    Icon,
    Dropdown,
    Breadcrumb
} from 'semantic-ui-react'

import DocExplorer from './DocExplorer';

import {
    iterateNodes, 
    findItem
} from 'lib/treeUtils';

import { docRefPicked } from './redux'

import { withPickedDocRef } from './withPickedDocRef';

const DocRefDropdownPicker = ({
    pickerId,
    documentTree,
    typeFilter,
    docRef,
    docRefPicked
}) => {
    let value = (!!docRef) ? docRef.uuid : '';
    
    let options = [];
    iterateNodes(documentTree, (lineage, node) => {
        // If we are filtering on type, check this now
        if (!!typeFilter && (typeFilter !== node.type)) {
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
    
    let onDocRefSelected = (event, data) => {
        let picked = findItem(documentTree, data.value);
        docRefPicked(pickerId, picked);
    }

    return (
        <Dropdown
            selection
            search
            options={options}
            value={value}
            onChange={onDocRefSelected}
            placeholder='Choose an option'
            />
    )
}

DocRefDropdownPicker.propTypes = {
    pickerId : PropTypes.string.isRequired,
    documentTree : PropTypes.object.isRequired,

    typeFilter : PropTypes.string,
    docRef : PropTypes.object,
    docRefPicked : PropTypes.func.isRequired
}

export default compose(
    connect(
        (state) => ({
            documentTree : state.explorerTree.documentTree
        }),
        {
            docRefPicked
        }
    ),
    withPickedDocRef()
)(DocRefDropdownPicker);