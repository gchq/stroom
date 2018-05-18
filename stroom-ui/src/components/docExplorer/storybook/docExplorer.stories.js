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

import { storiesOf, addDecorator } from '@storybook/react';
import { action } from '@storybook/addon-actions';
import { withNotes } from '@storybook/addon-notes';

import {
    DocExplorer,
    DocRef,
    Folder,
    DocRefModalPicker,
    DocRefDropdownPicker
} from '../index';

import {
    receiveDocTree
} from '../redux'

import markdown from './docExplorer.md';
import { 
    testTree, 
    fellowship, 
    sam, 
    gimli, 
    DOC_REF_TYPES 
} from './testTree'

import { ReduxDecorator } from 'lib/storybook/ReduxDecorator';

import {
    DragDropDecorator
} from 'lib/storybook/DragDropDecorator';

import { 
    testInitialisationDecorator
} from 'lib/storybook/testDataDecorator';

import ManagedPickerHarness from 'lib/storybook/ManagedPickerHarness';

class ManagedDocRefDropdownPicker extends ManagedPickerHarness {
    static defaultProps = {
        actionName: 'doc-ref-picked'
    }

    render() {
        return (
            <DocRefDropdownPicker 
                pickerId={this.props.pickerId}
                typeFilter={this.props.typeFilter}
                value={this.state.value}
                onChange={this.onChange.bind(this)} 
                />
        )
    }
}

class ManagedDocRefModalPicker extends ManagedPickerHarness {
    static defaultProps = {
        actionName: 'doc-ref-picked'
    }
    
    render() {
        return (
            <DocRefModalPicker 
                pickerId={this.props.pickerId}
                value={this.state.value}
                onChange={this.onChange.bind(this)} 
                />
        )
    }
}

storiesOf('Document Explorer', module)
    .addDecorator(testInitialisationDecorator(receiveDocTree, testTree))
    .addDecorator(ReduxDecorator) // must be recorder after/outside of the test initialisation decorators
    .addDecorator(DragDropDecorator)
    .add('Explorer Tree (multi-select, dnd)', () => 
        <DocExplorer 
            explorerId='multi-select-dnd'
            />
    )
    .add('Explorer Tree (single-select, no-dnd)', () => 
        <DocExplorer 
            explorerId='single-select-no-dnd'
            allowMultiSelect={false} 
            allowDragAndDrop={false
            }/>
    )
    .add('Explorer Tree (type filter to monster)', () => 
        <DocExplorer 
            explorerId='filtered-monster'
            typeFilter={DOC_REF_TYPES.MONSTER}
            />
    )
    .add('Explorer Tree (type filter to dictionary)', () => 
        <DocExplorer 
            explorerId='filtered-dict'
            typeFilter={DOC_REF_TYPES.DICTIONARY}
            />
    )
    .add('Doc Ref Picker (dropdown, no choice made)', () => 
        <ManagedDocRefDropdownPicker 
            pickerId='dropdown1' 
            />
    )
    .add('Doc Ref Picker (dropdown, choice made)', () => 
        <ManagedDocRefDropdownPicker 
            pickerId='dropdown2' 
            initialValue={sam} 
            />
    )
    .add('Doc Ref Picker (dropdown, filter to dictionaries)', () => 
        <ManagedDocRefDropdownPicker 
            pickerId='dropdown3' 
            typeFilter={DOC_REF_TYPES.DICTIONARY}
            />
    )
    .add('Doc Ref Picker (modal, no choice made)', () => 
        <ManagedDocRefModalPicker 
            pickerId='modal1'  
            />
    )
    .add('Doc Ref Picker (modal, choice made)', () => 
        <ManagedDocRefModalPicker 
            pickerId='modal2' 
            initialValue={gimli}
            />
    )
    .add('Doc Ref Picker (modal, filter to hobbits)', () => 
        <ManagedDocRefModalPicker 
            pickerId='modal3'  
            typeFilter={DOC_REF_TYPES.HOBBIT}
            />
    )
    // .add('Single Folder', withNotes(markdown)(() => <TestFolder folder={fellowship} />))
    // .add('Single DocRef', withNotes(markdown)(() => <TestDocRef docRef={sam} />))