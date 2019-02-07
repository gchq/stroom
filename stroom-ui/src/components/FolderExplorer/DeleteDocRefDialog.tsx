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
import { compose } from "recompose";
import { connect } from "react-redux";

import { GlobalStoreState } from "../../startup/reducers";
import { deleteDocuments } from "./explorerClient";
import ThemedConfirm from "../ThemedConfirm";

export interface Props {
  uuids?: Array<string>;
}

interface ConnectDispatch {
  deleteDocuments: typeof deleteDocuments;
}

interface EnhancedProps extends Props, ConnectDispatch {}

const enhance = compose<EnhancedProps, Props>(
  connect<{}, ConnectDispatch, Props, GlobalStoreState>(
    () => ({}),
    { deleteDocuments }
  )
);

const DeleteDocRefDialog = ({ uuids, deleteDocuments }: EnhancedProps) => (
  <ThemedConfirm
    onConfirm={() => {
      if (!!uuids) {
        deleteDocuments(uuids);
      }
    }}
    onCancel={() => console.log("fuck off")}
    isOpen={!!uuids}
    question={`Delete these doc refs? ${JSON.stringify(uuids)}?`}
  />
);

export default enhance(DeleteDocRefDialog);
