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
import { storiesOf } from "@storybook/react";

import fullTestData from "testing/data";
import useSelectableItemListing, {
  SelectionBehaviour,
} from "lib/useSelectableItemListing";
import DocRefListingEntry from "./DocRefListingEntry";

import { DocRefBreadcrumb } from "../DocRefBreadcrumb";
import JsonDebug from "testing/JsonDebug";
import Button from "../Button";
import { DocRefType } from "components/DocumentEditors/useDocumentApi/types/base";

const testFolder = fullTestData.documentTree;
const testDocRef = fullTestData.documentTree.children![0].children![0];

interface Props {
  docRefs: DocRefType[];
  dndIsOver?: boolean;
  dndCanDrop?: boolean;
  provideBreadcrumbs?: boolean;
}

const TestDocRefListingEntry: React.FunctionComponent<Props> = ({
  docRefs,
  dndIsOver,
  dndCanDrop,
  provideBreadcrumbs,
}) => {
  const [enteredFolder, enterFolder] = React.useState<DocRefType | undefined>(
    undefined,
  );
  const [openedDocRef, openDocRef] = React.useState<DocRefType | undefined>(
    undefined,
  );
  const [wentBack, setWentBack] = React.useState<boolean>(false);

  const goBack = () => setWentBack(true);
  const onClickClear = () => {
    enterFolder(undefined);
    openDocRef(undefined);
    setWentBack(false);
  };

  const {
    onKeyDown,
    toggleSelection,
    selectedItems: selectedDocRefs,
    highlightedItem: highlightedDocRef,
  } = useSelectableItemListing<DocRefType>({
    items: docRefs,
    openItem: openDocRef,
    getKey: React.useCallback((d) => d.uuid, []),
    enterItem: enterFolder,
    goBack,
    selectionBehaviour: SelectionBehaviour.MULTIPLE,
  });

  return (
    <div style={{ width: "50%" }}>
      <div
        tabIndex={0}
        onKeyDown={onKeyDown}
        style={{ borderStyle: "dashed", borderWidth: "2px" }}
      >
        {docRefs &&
          docRefs.map((docRef) => (
            <DocRefListingEntry
              key={docRef.uuid}
              {...{
                docRef,
                openDocRef,
                enterFolder,
                dndCanDrop,
                dndIsOver,
                toggleSelection,
                selectedDocRefs,
                highlightedDocRef,
              }}
            >
              {provideBreadcrumbs && (
                <DocRefBreadcrumb
                  docRefUuid={docRef.uuid}
                  openDocRef={openDocRef}
                />
              )}
            </DocRefListingEntry>
          ))}
      </div>
      <Button text="Clear" onClick={onClickClear} />
      <JsonDebug value={{ wentBack, openedDocRef, enteredFolder }} />
    </div>
  );
};

storiesOf("Doc Ref/Listing Entry", module)
  .add("docRef", () => <TestDocRefListingEntry docRefs={[testDocRef]} />)
  .add("docRef isOver canDrop", () => (
    <TestDocRefListingEntry docRefs={[testDocRef]} dndIsOver dndCanDrop />
  ))
  .add("docRef isOver cannotDrop", () => (
    <TestDocRefListingEntry
      docRefs={[testDocRef]}
      dndIsOver
      dndCanDrop={false}
    />
  ))
  .add("folder", () => (
    <TestDocRefListingEntry docRefs={testFolder.children!} />
  ))
  .add("folder (w/breadcrumbs)", () => (
    <TestDocRefListingEntry docRefs={testFolder.children!} provideBreadcrumbs />
  ));
