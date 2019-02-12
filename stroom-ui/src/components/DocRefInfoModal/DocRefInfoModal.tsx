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
import { useState, useEffect } from "react";
import { connect } from "react-redux";
import { compose } from "recompose";

import Loader from "../Loader";
import ThemedModal from "../ThemedModal";
import IconHeader from "../IconHeader";
import Button from "../Button";
import { GlobalStoreState } from "../../startup/reducers";
import { fetchDocInfo } from "../FolderExplorer/explorerClient";
import { DocRefType, DocRefInfoType } from "../../types";

export interface Props {
  docRef?: DocRefType;
  isOpen: boolean;
  onCloseDialog: () => void;
}
interface ConnectState {
  docRefInfoByUuid: { [s: string]: DocRefInfoType };
}
interface ConnectDispatch {
  fetchDocInfo: typeof fetchDocInfo;
}

export interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ folderExplorer: { docRefInfoByUuid } }) => ({
      docRefInfoByUuid
    }),
    {
      fetchDocInfo
    }
  )
);

const doNothing = () => {};

const DocRefInfoModal = ({
  isOpen,
  onCloseDialog,
  docRef,
  docRefInfoByUuid,
  fetchDocInfo
}: EnhancedProps) => {
  useEffect(() => {
    if (!!docRef) {
      fetchDocInfo(docRef);
    }
  });

  if (!isOpen || !docRef) {
    return null;
  }

  const docRefInfo = docRefInfoByUuid[docRef.uuid];

  if (!docRefInfo) {
    return <Loader message="Awaiting DocRef info..." />;
  }

  const { createTime, updateTime } = docRefInfo;

  const formattedCreateTime = new Date(createTime).toLocaleString("en-GB", {
    timeZone: "UTC"
  });
  const formattedUpdateTime = new Date(updateTime).toLocaleString("en-GB", {
    timeZone: "UTC"
  });

  return (
    <ThemedModal
      isOpen={isOpen}
      onRequestClose={onCloseDialog}
      header={<IconHeader icon="info" text="Document Information" />}
      content={
        <form className="DocRefInfo">
          <div className="DocRefInfo__type">
            <label>Type</label>
            <input
              type="text"
              value={docRefInfo.docRef.type}
              onChange={doNothing}
            />
          </div>
          <div className="DocRefInfo__uuid">
            <label>UUID</label>
            <input
              type="text"
              value={docRefInfo.docRef.uuid}
              onChange={doNothing}
            />
          </div>
          <div className="DocRefInfo__name">
            <label>Name</label>
            <input
              type="text"
              value={docRefInfo.docRef.name}
              onChange={doNothing}
            />
          </div>

          <div className="DocRefInfo__createdBy">
            <label>Created by</label>
            <input
              type="text"
              value={docRefInfo.createUser}
              onChange={doNothing}
            />
          </div>
          <div className="DocRefInfo__createdOn">
            <label>at</label>
            <input
              type="text"
              value={formattedCreateTime}
              onChange={doNothing}
            />
          </div>
          <div className="DocRefInfo__updatedBy">
            <label>Updated by</label>
            <input
              type="text"
              value={docRefInfo.updateUser}
              onChange={doNothing}
            />
          </div>
          <div className="DocRefInfo__updatedOn">
            <label>at</label>
            <input
              type="text"
              value={formattedUpdateTime}
              onChange={doNothing}
            />
          </div>
          <div className="DocRefInfo__otherInfo">
            <label>Other Info</label>
            <input
              type="text"
              value={docRefInfo.otherInfo}
              onChange={doNothing}
            />
          </div>
        </form>
      }
      actions={<Button onClick={onCloseDialog} text="Close" />}
    />
  );
};

/**
 * These are the things returned by the custom hook that allow the owning component to interact
 * with this dialog.
 */
export type UseDocRefInfoDialog = {
  /**
   * The owning component is ready to start a deletion process.
   * Calling this will open the dialog, and setup the UUIDs
   */
  showDialog: (docRef: DocRefType) => void;
  /**
   * These are the properties that the owning component can just give to the Dialog component
   * using destructing.
   */
  componentProps: Props;
};

/**
 * This is a React custom hook that sets up things required by the owning component.
 */
export const useDocRefInfoDialog = (): UseDocRefInfoDialog => {
  const [docRef, setDocRef] = useState<DocRefType | undefined>(undefined);
  const [isOpen, setIsOpen] = useState<boolean>(false);

  return {
    componentProps: {
      docRef,
      isOpen,
      onCloseDialog: () => {
        setIsOpen(false);
        setDocRef(undefined);
      }
    },
    showDialog: (_docRef: DocRefType) => {
      setIsOpen(true);
      setDocRef(_docRef);
    }
  };
};

export default enhance(DocRefInfoModal);
