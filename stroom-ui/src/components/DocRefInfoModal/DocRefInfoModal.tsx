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
import { connect } from "react-redux";
import {
  compose,
  branch,
  renderNothing,
  renderComponent,
  withProps
} from "recompose";

import Loader from "../Loader";
import ThemedModal from "../ThemedModal";
import { actionCreators } from "./redux";
import IconHeader from "../IconHeader";
import Button from "../Button";
import { DocRefInfoType } from "../../types";
import { GlobalStoreState } from "../../startup/reducers";

const { docRefInfoClosed } = actionCreators;

export interface Props {}
interface ConnectState {
  isOpen: boolean;
  docRefInfo?: DocRefInfoType;
}
interface ConnectDispatch {
  docRefInfoClosed: typeof docRefInfoClosed;
}
interface WithProps {
  formattedCreateTime: string;
  formattedUpdateTime: string;
}
export interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    WithProps {
  docRefInfo: DocRefInfoType;
}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ docRefInfo: { isOpen, docRefInfo } }, props) => ({
      isOpen,
      docRefInfo
    }),
    { docRefInfoClosed }
  ),
  branch(({ isOpen }) => !isOpen, renderNothing),
  branch(
    ({ docRefInfo }) => !docRefInfo,
    renderComponent(() => <Loader message="Awaiting DocRef info..." />)
  ),
  withProps(({ docRefInfo: { createTime, updateTime } }) => ({
    formattedCreateTime: new Date(createTime).toLocaleString("en-GB", {
      timeZone: "UTC"
    }),
    formattedUpdateTime: new Date(updateTime).toLocaleString("en-GB", {
      timeZone: "UTC"
    })
  }))
);

const doNothing = () => {};

const DocRefInfoModal = ({
  isOpen,
  docRefInfo,
  docRefInfoClosed,
  formattedCreateTime,
  formattedUpdateTime
}: EnhancedProps) => (
  <ThemedModal
    isOpen={isOpen}
    onRequestClose={docRefInfoClosed}
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
          <input type="text" value={formattedCreateTime} onChange={doNothing} />
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
          <input type="text" value={formattedUpdateTime} onChange={doNothing} />
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
    actions={<Button onClick={docRefInfoClosed} text="Close" />}
  />
);

export default enhance(DocRefInfoModal);
