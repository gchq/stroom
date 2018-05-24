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
    receiveDocTree,
    docRefPicked
} from '../redux'

import markdown from './docExplorer.md';
import { 
    testTree,
    DOC_REF_TYPES 
} from './testTree'

import {
    pickRandomItem
} from 'lib/treeUtils';

import { ReduxDecoratorWithInitialisation } from 'lib/storybook/ReduxDecorator';

import {
    DragDropDecorator
} from 'lib/storybook/DragDropDecorator';

storiesOf('Document Explorer', module)
    .addDecorator(ReduxDecoratorWithInitialisation((store) => {
        store.dispatch(receiveDocTree(testTree));
        store.dispatch(docRefPicked('dropdown2', pickRandomItem(testTree, (l, n) => n.type === DOC_REF_TYPES.XSLT)));
        store.dispatch(docRefPicked('modal2', pickRandomItem(testTree, (l, n) => n.type === DOC_REF_TYPES.Pipeline)));
    }))
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
            explorerId='filtered-xslt'
            typeFilter={DOC_REF_TYPES.XSLT}
            />
    )
    .add('Explorer Tree (type filter to dictionary)', () => 
        <DocExplorer 
            explorerId='filtered-dict'
            typeFilter={DOC_REF_TYPES.DICTIONARY}
            />
    )
    .add('Doc Ref Picker (dropdown, no choice made)', () => 
        <DocRefDropdownPicker 
            pickerId='dropdown1' 
            />
    )
    .add('Doc Ref Picker (dropdown, choice made)', () => 
        <DocRefDropdownPicker 
            pickerId='dropdown2'
            />
    )
    .add('Doc Ref Picker (dropdown, filter to dictionaries)', () => 
        <DocRefDropdownPicker 
            pickerId='dropdown3' 
            typeFilter={DOC_REF_TYPES.DICTIONARY}
            />
    )
    .add('Doc Ref Picker (modal, no choice made)', () => 
        <DocRefModalPicker 
            pickerId='modal1'  
            />
    )
    .add('Doc Ref Picker (modal, choice made)', () => 
        <DocRefModalPicker 
            pickerId='modal2' 
            />
    )
    .add('Doc Ref Picker (modal, filter to hobbits)', () => 
        <DocRefModalPicker 
            pickerId='modal3'  
            typeFilter={DOC_REF_TYPES.HOBBIT}
            />
    )