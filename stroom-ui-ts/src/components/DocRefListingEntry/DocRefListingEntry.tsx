import * as React from "react";

import { connect } from "react-redux";
import { compose, withHandlers, withProps } from "recompose";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";

import { StoreState as KeyIsDownStoreState } from "../../lib/KeyIsDown";
import { DocRefType, DocRefConsumer } from "../../types";
import DocRefImage from "../DocRefImage";
import { GlobalStoreState } from "../../startup/reducers";
import {
  actionCreators as selectableItemActionCreators,
  defaultStatePerId
} from "../../lib/withSelectableItemListing";

const { selectionToggled } = selectableItemActionCreators;

export interface Props {
  listingId: string;
  docRef: DocRefType;
  isSelected?: boolean;
  dndIsOver?: boolean;
  dndCanDrop?: boolean;
  openDocRef: DocRefConsumer;
  enterFolder?: DocRefConsumer;
}

export interface ConnectProps {
  keyIsDown: KeyIsDownStoreState;
  selectionToggled: typeof selectionToggled;
}

export interface Handlers {
  onSelect: React.MouseEventHandler<HTMLDivElement>;
  onOpenDocRef: React.MouseEventHandler<HTMLDivElement>;
  onEnterFolder: React.MouseEventHandler<HTMLDivElement>;
}

export interface AddedProps {
  className: string;
}

export interface EnhancedProps
  extends Props,
    ConnectProps,
    Handlers,
    AddedProps {}

const enhance = compose<EnhancedProps, Props>(
  connect(
    (
      { keyIsDown, selectableItemListings }: GlobalStoreState,
      { listingId, docRef }: Props
    ) => {
      const { selectedItems = [], focussedItem } =
        selectableItemListings[listingId] || defaultStatePerId;
      const isSelected = selectedItems
        .map((d: DocRefType) => d.uuid)
        .includes(docRef.uuid);
      const inFocus = focussedItem && focussedItem.uuid === docRef.uuid;

      return {
        isSelected,
        inFocus,
        keyIsDown
      };
    },
    { selectionToggled }
  ),
  withHandlers<Props & ConnectProps, Handlers>({
    onSelect: ({ listingId, docRef, keyIsDown, selectionToggled }) => e => {
      selectionToggled(listingId, docRef.uuid, keyIsDown);
      e.preventDefault();
      e.stopPropagation();
    },
    onOpenDocRef: ({ openDocRef, docRef }) => e => {
      openDocRef(docRef);
      e.preventDefault();
      e.stopPropagation();
    },
    onEnterFolder: ({ openDocRef, enterFolder, docRef }) => e => {
      if (enterFolder) {
        enterFolder(docRef);
      } else {
        openDocRef(docRef); // fall back to this
      }
      e.stopPropagation();
      e.preventDefault();
    }
  }),
  withProps(({ dndIsOver, dndCanDrop, isSelected, inFocus }) => {
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

    return {
      className: additionalClasses.join(" ")
    };
  })
);

let DocRefListingEntry = ({
  className,
  docRef,
  onSelect,
  onOpenDocRef,
  onEnterFolder
}: EnhancedProps) => (
  <div className={className} onClick={onSelect}>
    <DocRefImage docRefType={docRef.type} />
    <div className="DocRefListingEntry__name" onClick={onOpenDocRef}>
      {docRef.name}
    </div>
    <div className="DocRefListingEntry__space" />
    {docRef.type === "System" ||
      (docRef.type === "Folder" && (
        <div onClick={onEnterFolder}>
          <FontAwesomeIcon
            className="DocRefListingEntry__icon"
            size="lg"
            icon="angle-right"
          />
        </div>
      ))}
  </div>
);

export default enhance(DocRefListingEntry);
