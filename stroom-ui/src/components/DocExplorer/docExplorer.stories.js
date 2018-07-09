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

import {
  DocExplorer,
  DocRef,
  Folder,
  DocPickerModal,
  PermissionInheritancePicker,
  permissionInheritanceValues,
  DocPicker,
} from './index';
import { actionCreators } from './redux';
import { testTree, fromSetupSampleData, testDocRefsTypes } from './test';
import { pickRandomItem } from 'lib/treeUtils';

import { ReduxDecoratorWithInitialisation, ReduxDecorator } from 'lib/storybook/ReduxDecorator';
import { PollyDecorator } from 'lib/storybook/PollyDecorator';
import { DragDropDecorator } from 'lib/storybook/DragDropDecorator';

import 'styles/main.css';

const { docRefPicked, permissionInheritancePicked } = actionCreators;

storiesOf('Document Explorer (small testTree)', module)
  .addDecorator(PollyDecorator({ documentTree: testTree, docRefTypes: testDocRefsTypes }))
  .addDecorator(ReduxDecorator)
  .addDecorator(DragDropDecorator)
  .add('Explorer Tree', () => <DocExplorer explorerId="dev-server" />);

storiesOf('Document Explorer (from setupSampleData)', module)
  .addDecorator(PollyDecorator({ documentTree: fromSetupSampleData, docRefTypes: testDocRefsTypes }))
  .addDecorator(ReduxDecorator)
  .addDecorator(DragDropDecorator)
  .add('Explorer Tree (multi-select, dnd)', () => <DocExplorer explorerId="multi-select-dnd" />)
  .add('Explorer Tree (single-select, no-dnd)', () => (
    <DocExplorer
      explorerId="single-select-no-dnd"
      allowMultiSelect={false}
      allowDragAndDrop={false}
    />
  ))
  .add('Explorer Tree (type filter to XSLT)', () => (
    <DocExplorer explorerId="filtered-xslt" typeFilter="XSLT" />
  ))
  .add('Explorer Tree (type filter to dictionary)', () => (
    <DocExplorer explorerId="filtered-dict" typeFilter="Dictionary" />
  ));

storiesOf('Doc Ref Modal Picker', module)
  .addDecorator(PollyDecorator({ documentTree: fromSetupSampleData, docRefTypes: testDocRefsTypes }))
  .addDecorator(ReduxDecoratorWithInitialisation((store) => {
    store.dispatch(docRefPicked(
      'modal2',
      pickRandomItem(fromSetupSampleData, (l, n) => n.type === 'Pipeline'),
    ));
  }))
  .addDecorator(DragDropDecorator)
  .add('Doc Ref Picker (modal, no choice made)', () => <DocPickerModal pickerId="modal1" />)
  .add('Doc Ref Picker (modal, choice made)', () => <DocPickerModal pickerId="modal2" />)
  .add('Doc Ref Picker (modal, filter to pipeline)', () => (
    <DocPickerModal pickerId="modal3" typeFilter="Pipeline" />
  ))
  .add('Doc Ref Picker (modal, filter to folders)', () => (
    <DocPickerModal pickerId="modal4" typeFilter="Folder" />
  ));

storiesOf('Doc Ref Picker', module)
  .addDecorator(PollyDecorator({ documentTree: fromSetupSampleData, docRefTypes: testDocRefsTypes }))
  .addDecorator(ReduxDecorator)
  .addDecorator(DragDropDecorator)
  .add('Doc Picker', () => <DocPicker explorerId="picker1" />)
  .add('Doc Picker (folders only)', () => <DocPicker explorerId="picker2" foldersOnly />);

storiesOf('Permission Inheritance Picker', module)
  .addDecorator(PollyDecorator({ documentTree: fromSetupSampleData, docRefTypes: testDocRefsTypes }))
  .addDecorator(ReduxDecoratorWithInitialisation((store) => {
    store.dispatch(permissionInheritancePicked('pi2', permissionInheritanceValues.DESTINATION));
  }))
  .add('Permission Inheritance Picker', () => <PermissionInheritancePicker pickerId="pi1" />)
  .add('Permission Inheritance Picker (choice made)', () => (
    <PermissionInheritancePicker pickerId="pi2" />
  ));
