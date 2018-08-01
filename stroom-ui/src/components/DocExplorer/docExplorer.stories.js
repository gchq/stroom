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
import React from 'react';

import { storiesOf, addDecorator } from '@storybook/react';
import StoryRouter from 'storybook-react-router';
import { compose, withState } from 'recompose';

import { DocExplorer, DocRefInfoModal, DocRef, Folder, DocPickerModal } from './index';
import { actionCreators } from './redux';
import { testTree, fromSetupSampleData, testDocRefsTypes } from './test';
import { pickRandomItem } from 'lib/treeUtils';

import { ReduxDecoratorWithInitialisation, ReduxDecorator } from 'lib/storybook/ReduxDecorator';
import { PollyDecorator } from 'lib/storybook/PollyDecorator';
import { DragDropDecorator } from 'lib/storybook/DragDropDecorator';
import { KeyIsDownDecorator } from 'lib/storybook/KeyIsDownDecorator';
import { ControlledInputDecorator } from 'lib/storybook/ControlledInputDecorator';

import 'styles/main.css';

const { permissionInheritancePicked, docRefInfoReceived, docRefInfoOpened } = actionCreators;

storiesOf('Document Explorer (small)', module)
  .addDecorator(PollyDecorator({ documentTree: testTree, docRefTypes: testDocRefsTypes }))
  .addDecorator(KeyIsDownDecorator())
  .addDecorator(ReduxDecorator)
  .addDecorator(DragDropDecorator)
  .addDecorator(StoryRouter())
  .add('Explorer Tree', () => <DocExplorer explorerId="dev-server" />);

storiesOf('Document Explorer (setupSampleData)', module)
  .addDecorator(PollyDecorator({ documentTree: fromSetupSampleData, docRefTypes: testDocRefsTypes }))
  .addDecorator(KeyIsDownDecorator())
  .addDecorator(ReduxDecorator)
  .addDecorator(DragDropDecorator)
  .addDecorator(StoryRouter())
  .add('Explorer Tree', () => <DocExplorer explorerId="multi-select-dnd" />);

const timeCreated = Date.now();

storiesOf('Doc Ref Info Modal', module)
  .addDecorator(ReduxDecoratorWithInitialisation((store) => {
    const docRef = {
      type: 'Animal',
      name: 'Tiger',
      uuid: '1234456789',
    };
    store.dispatch(docRefInfoOpened(docRef));
    store.dispatch(docRefInfoReceived({
      docRef,
      createTime: timeCreated,
      updateTime: Date.now(),
      createUser: 'me',
      updateUser: 'you',
      otherInfo: 'I am test data',
    }));
  }))
  .add('Doc Ref Info Modal', () => <DocRefInfoModal />);

storiesOf('Doc Ref Modal Picker', module)
  .addDecorator(ControlledInputDecorator) // must be the 'first' one
  .addDecorator(PollyDecorator({ documentTree: fromSetupSampleData, docRefTypes: testDocRefsTypes }))
  .addDecorator(ReduxDecorator)
  .add('Doc Ref Picker', () => <DocPickerModal explorerId="modal1" />)
  .add('Doc Ref Picker (filter to pipeline)', () => (
    <DocPickerModal explorerId="modal2" typeFilters={['Pipeline']} />
  ))
  .add('Doc Ref Picker (filter to feed AND dictionary)', () => (
    <DocPickerModal explorerId="modal3" typeFilters={['Feed', 'Dictionary']} />
  ))
  .add('Doc Ref Picker (filter to Folders)', () => (
    <DocPickerModal explorerId="modal3" typeFilters={['Folder']} />
  ));
