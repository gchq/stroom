import * as React from "react";

import DocRefImage from "../DocRefImage";
import { Props } from "./types";
import { DocRefType } from "components/DocumentEditors/useDocumentApi/types/base";

const DocRefListingEntry: React.FunctionComponent<Props> = ({
  docRef,
  dndIsOver,
  dndCanDrop,
  openDocRef,
  children,
  toggleSelection,
  selectedDocRefs,
  highlightedDocRef,
}) => {
  const onSelect: React.MouseEventHandler<HTMLDivElement> = React.useCallback(
    (e) => {
      toggleSelection(docRef.uuid);
      e.preventDefault();
      e.stopPropagation();
    },
    [toggleSelection, docRef],
  );

  const onOpenDocRef: React.MouseEventHandler<HTMLDivElement> = React.useCallback(
    (e) => {
      openDocRef(docRef);
      e.preventDefault();
      e.stopPropagation();
    },
    [openDocRef, docRef],
  );

  const className = React.useMemo(() => {
    const additionalClasses = [];
    additionalClasses.push("DocRefListingEntry");
    additionalClasses.push("hoverable");
    additionalClasses.push("clickable");

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

    const hasHighlight: boolean =
      !!highlightedDocRef && highlightedDocRef.uuid === docRef.uuid;
    const isSelected: boolean =
      selectedDocRefs.map((d: DocRefType) => d.uuid).indexOf(docRef.uuid) !==
      -1;

    if (isSelected) {
      additionalClasses.push("selected-item");
    }
    if (hasHighlight) {
      additionalClasses.push("highlighted-item");
    }

    return additionalClasses.join(" ");
  }, [docRef, selectedDocRefs, highlightedDocRef, dndCanDrop, dndIsOver]);

  return (
    <div className={className} onClick={onSelect}>
      <div className="DocRefListingEntry__docRefImage">
        <DocRefImage docRefType={docRef.type} />
      </div>
      <div className="DocRefListingEntry__name" onClick={onOpenDocRef}>
        {docRef.name}
      </div>
      <div className="DocRefListing__children">{children}</div>
    </div>
  );
};

export default DocRefListingEntry;
