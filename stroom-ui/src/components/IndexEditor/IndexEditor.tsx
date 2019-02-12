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
import { useEffect } from "react";
import { compose } from "recompose";
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

export interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

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
  )
);

const IndexEditor = ({ indexUuid, indexState }: EnhancedProps) => {
  useEffect(() => {
    fetchIndex(indexUuid);
  }, []);

  if (!indexState) {
    return <Loader message="Loading XSLT..." />;
  }

  const { indexData, isDirty, isSaving } = indexState;

  const actionBarItems: Array<ButtonProps> = [
    {
      icon: "save",
      disabled: !(isDirty || isSaving),
      title: isSaving ? "Saving..." : isDirty ? "Save" : "Saved",
      onClick: () => saveIndex(indexUuid)
    }
  ];

  return (
    <DocRefEditor docRefUuid={indexUuid} actionBarItems={actionBarItems}>
      <ThemedAceEditor
        style={{ width: "100%", height: "100%", minHeight: "25rem" }}
        name={`${indexUuid}-ace-editor`}
        mode="xml"
        value={indexData}
        onChange={v => {
          if (v !== indexData) indexUpdated(indexUuid, v);
        }}
      />
    </DocRefEditor>
  );
};

export default enhance(IndexEditor);
