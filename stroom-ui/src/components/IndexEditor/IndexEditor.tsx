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
import {
  compose,
  lifecycle,
  renderComponent,
  branch,
  withHandlers,
  withProps
} from "recompose";
import { connect } from "react-redux";

import DocRefEditor from "../DocRefEditor";
import { Props as ButtonProps } from "../Button";
import Loader from "../Loader";
import { fetchIndex, saveIndex } from "./client";
import ThemedAceEditor from "../ThemedAceEditor";
import { actionCreators, StoreStateById } from "./redux";
import { GlobalStoreState } from "../../startup/reducers";

const { indexUpdated } = actionCreators;

export interface Props {
  indexUuid: string;
}

interface ConnectState {
  indexState: StoreStateById;
}

interface ConnectDispatch {
  fetchIndex: typeof fetchIndex;
  indexUpdated: typeof indexUpdated;
  saveIndex: typeof saveIndex;
}

interface WithHandlers {
  onContentChange: (a: string) => any;
  onClickSave: React.MouseEventHandler;
}

interface WithProps {
  actionBarItems: Array<ButtonProps>;
}

export interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    WithHandlers,
    WithProps {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ indexEditor }, { indexUuid }) => ({
      indexState: indexEditor[indexUuid]
    }),
    {
      fetchIndex,
      indexUpdated,
      saveIndex
    }
  ),
  lifecycle<Props & ConnectState & ConnectDispatch, {}>({
    componentDidMount() {
      const { fetchIndex, indexUuid } = this.props;

      fetchIndex(indexUuid);
    }
  }),
  branch(
    ({ indexState }) => !indexState,
    renderComponent(() => <Loader message="Loading XSLT..." />)
  ),
  withHandlers<Props & ConnectState & ConnectDispatch, WithHandlers>({
    onContentChange: ({
      indexUpdated,
      indexUuid,
      indexState: { indexData }
    }) => newValue => {
      if (newValue !== indexData) indexUpdated(indexUuid, newValue);
    },
    onClickSave: ({ saveIndex, indexUuid }) => e => saveIndex(indexUuid)
  }),
  withProps(({ indexState: { isDirty, isSaving }, onClickSave }) => ({
    actionBarItems: [
      {
        icon: "save",
        disabled: !(isDirty || isSaving),
        title: isSaving ? "Saving..." : isDirty ? "Save" : "Saved",
        onClick: onClickSave
      }
    ]
  }))
);

const IndexEditor = ({
  indexUuid,
  indexState: { indexData },
  onContentChange,
  actionBarItems
}: EnhancedProps) => (
  <DocRefEditor docRefUuid={indexUuid} actionBarItems={actionBarItems}>
    <ThemedAceEditor
      style={{ width: "100%", height: "100%", minHeight: "25rem" }}
      name={`${indexUuid}-ace-editor`}
      mode="xml"
      value={indexData}
      onChange={onContentChange}
    />
  </DocRefEditor>
);

export default enhance(IndexEditor);
