import * as React from "react";

import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";

import { DocRefType, DocRefConsumer } from "../../types";
import DocRefImage from "../DocRefImage";

export interface Props {
  docRef: DocRefType;
  dndIsOver?: boolean;
  dndCanDrop?: boolean;
  openDocRef: DocRefConsumer;
  enterFolder?: DocRefConsumer;
  children?: React.ReactNode;
  selectionToggled: (itemKey: string) => void;
  selectedDocRefs: Array<DocRefType>;
  focussedDocRef?: DocRefType;
}

let DocRefListingEntry = ({
  docRef,
  dndIsOver,
  dndCanDrop,
  openDocRef,
  enterFolder,
  children,
  selectionToggled,
  selectedDocRefs,
  focussedDocRef
}: Props) => {
  const isSelected: boolean =
    selectedDocRefs.map((d: DocRefType) => d.uuid).indexOf(docRef.uuid) !== -1;
  const inFocus: boolean =
    !!focussedDocRef && focussedDocRef.uuid === docRef.uuid;

  const onSelect: React.MouseEventHandler<HTMLDivElement> = e => {
    selectionToggled(docRef.uuid);
    e.preventDefault();
    e.stopPropagation();
  };
  const onOpenDocRef: React.MouseEventHandler<HTMLDivElement> = e => {
    openDocRef(docRef);
    e.preventDefault();
    e.stopPropagation();
  };
  const onEnterFolder: React.MouseEventHandler<HTMLDivElement> = e => {
    if (enterFolder) {
      enterFolder(docRef);
    } else {
      openDocRef(docRef); // fall back to this
    }
    e.stopPropagation();
    e.preventDefault();
  };

  const additionalClasses = [];
  additionalClasses.push("DocRefListingEntry");
  additionalClasses.push("hoverable");

  if (dndIsOver) {
    additionalClasses.push("dndIsOver");
  }
  if (dndIsOver) {
    if (dndCanDrop) {
      additionalClasses.push("canDrop");
    } else {
      additionalClasses.push("cannotDrop");
    }
  }

  if (isSelected) {
    additionalClasses.push("selected");
  }
  if (inFocus) {
    additionalClasses.push("inFocus");
  }

  let canEnterFolder: boolean =
    docRef.type === "System" || docRef.type === "Folder";

  const className = additionalClasses.join(" ");

  return (
    <div className={className} onClick={onSelect}>
      <DocRefImage
        className="DocRefListingEntry__docRefImage"
        docRefType={docRef.type}
      />
      <div className="DocRefListingEntry__name" onClick={onOpenDocRef}>
        {docRef.name}
      </div>
      {canEnterFolder && (
        <div
          className="DocRefListingEntry__enterFolderIcon"
          onClick={onEnterFolder}
        >
          <FontAwesomeIcon size="lg" icon="angle-right" />
        </div>
      )}
      <div className="DocRefListing__children">{children}</div>
    </div>
  );
};

export default DocRefListingEntry;
