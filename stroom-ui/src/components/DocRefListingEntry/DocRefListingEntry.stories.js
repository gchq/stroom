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
import { compose, withStateHandlers } from 'recompose';
import { storiesOf, addDecorator } from '@storybook/react';
import uuidv4 from 'uuid/v4';

import { fromSetupSampleData } from 'components/FolderExplorer/test';
import withSelectableItemListing from 'lib/withSelectableItemListing';
import DocRefListingEntry from './DocRefListingEntry';

const testFolder = fromSetupSampleData.children[0];
const testDocRef = fromSetupSampleData.children[0].children[0].children[0];

const enhance = compose(
  withStateHandlers(
    ({ enteredFolder, openedDocRef, wentBack = false }) => ({
      enteredFolder,
      openedDocRef,
      wentBack,
    }),
    {
      enterFolder: () => enteredFolder => ({ enteredFolder }),
      openDocRef: () => openedDocRef => ({ openedDocRef }),
      goBack: () => () => ({ wentBack: true }),
      onClickClear: () => () => ({
        enteredFolder: undefined,
        openedDocRef: undefined,
        wentBack: false,
      }),
    },
  ),
  withSelectableItemListing(({
    listingId, docRefs, openDocRef, goBack, enterFolder,
  }) => ({
    listingId,
    items: docRefs,
    openItem: openDocRef,
    getKey: d => d.uuid,
    enterItem: enterFolder,
    goBack,
  })),
);

let TestDocRefListingEntry = ({
  listingId,
  onClickClear,
  enteredFolder,
  openedDocRef,
  wentBack,
  openDocRef,
  enterFolder,
  docRefs,
  onKeyDownWithShortcuts,
  dndIsOver,
  dndCanDrop,
}) => (
  <div style={{ width: '50%' }}>
    <div tabIndex={0} onKeyDown={onKeyDownWithShortcuts}>
      {docRefs.map(docRef => (
        <DocRefListingEntry
          key={docRef.uuid}
          listingId={listingId}
          docRef={docRef}
          openDocRef={openDocRef}
          enterFolder={enterFolder}
          dndIsOver={dndIsOver}
          dndCanDrop={dndCanDrop}
        />
      ))}
    </div>
    <div>
      <label>Entered Folder</label>
      {enteredFolder && enteredFolder.name}
    </div>
    <div>
      <label>Opened Doc Ref</label>
      {openedDocRef && openedDocRef.name}
    </div>
    <div>
      <label>Went Back</label>
      {wentBack ? 'true' : 'false'}
    </div>
    <button onClick={onClickClear}>Clear</button>
  </div>
);

TestDocRefListingEntry = enhance(TestDocRefListingEntry);

storiesOf('Doc Ref Listing Entry', module)
  .add('docRef', props => <TestDocRefListingEntry listingId={uuidv4()} docRefs={[testDocRef]} />)
  .add('docRef isOver canDrop', props => (
    <TestDocRefListingEntry
      listingId={uuidv4()}
      docRefs={[testDocRef]}
      dndIsOver
      dndCanDrop
    />
  ))
  .add('docRef isOver cannotDrop', props => (
    <TestDocRefListingEntry
      listingId={uuidv4()}
      docRefs={[testDocRef]}
      dndIsOver
      dndCanDrop={false}
    />
  ))
  .add('folder', props => (
    <TestDocRefListingEntry listingId={uuidv4()} docRefs={testFolder.children} />
  ));
