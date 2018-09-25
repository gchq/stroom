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
import * as React from "react";
import { compose, withStateHandlers } from "recompose";

import { DocRefType, DocRefConsumer } from "../../../types";
import withSelectableItemListing, {
  LifecycleProps as SelectableItemListingHandlers
} from "../../../lib/withSelectableItemListing";
import DocRefListingEntry from "../DocRefListingEntry";

export interface Props {
  listingId: string;
  dndIsOver: boolean;
  dndCanDrop: boolean;
  docRefs: Array<DocRefType>;
}

export interface StateProps {
  enteredFolder: DocRefType;
  openedDocRef: DocRefType;
  wentBack: boolean;
}

export interface StateHandlers {
  enterFolder: DocRefConsumer;
  openDocRef: DocRefConsumer;
  goBack: () => void;
  onClickClear: React.MouseEventHandler<HTMLButtonElement>;
}

export interface EnhancedProps
  extends Props,
    StateProps,
    StateHandlers,
    SelectableItemListingHandlers {}

const enhance = compose<EnhancedProps, Props>(
  withStateHandlers(
    ({ enteredFolder, openedDocRef, wentBack = false }: StateProps) => ({
      enteredFolder,
      openedDocRef,
      wentBack
    }),
    {
      enterFolder: () => (enteredFolder: DocRefType) => ({ enteredFolder }),
      openDocRef: () => (openedDocRef: DocRefType) => ({ openedDocRef }),
      goBack: () => () => ({ wentBack: true }),
      onClickClear: () => () => ({
        enteredFolder: undefined,
        openedDocRef: undefined,
        wentBack: false
      })
    }
  ),
  withSelectableItemListing<DocRefType>(
    ({ listingId, docRefs, openDocRef, goBack, enterFolder }) => ({
      listingId,
      items: docRefs,
      openItem: openDocRef,
      getKey: d => d.uuid,
      enterItem: enterFolder,
      goBack
    })
  )
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
  dndCanDrop
}: EnhancedProps) => (
  <div style={{ width: "50%" }}>
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
      <input readOnly value={enteredFolder ? enteredFolder.name : ""} />
    </div>
    <div>
      <label>Opened Doc Ref</label>
      <input readOnly value={openedDocRef ? openedDocRef.name : ""} />
    </div>
    <div>
      <label>Went Back</label>
      <input type="checkbox" readOnly checked={wentBack} />
    </div>
    <button onClick={onClickClear}>Clear</button>
  </div>
);

export default enhance(TestDocRefListingEntry);
