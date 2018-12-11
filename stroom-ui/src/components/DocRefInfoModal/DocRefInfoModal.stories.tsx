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
import { connect } from "react-redux";
import { compose, lifecycle } from "recompose";

import StroomDecorator from "../../lib/storybook/StroomDecorator";
import { DocRefInfoModal } from ".";
import { actionCreators } from "./redux";

import { DocRefInfoType } from "../../types";
import { GlobalStoreState } from "../../startup/reducers";

import "../../styles/main.css";

const { docRefInfoReceived, docRefInfoOpened } = actionCreators;

const timeCreated = Date.now();

interface Props {
  testDocRefWithInfo: DocRefInfoType;
}
interface ConnectState {}
interface ConnectDispatch {
  docRefInfoOpened: typeof docRefInfoOpened;
  docRefInfoReceived: typeof docRefInfoReceived;
}
interface PassedOnProps {}

const enhance = compose<PassedOnProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    undefined,
    {
      docRefInfoOpened,
      docRefInfoReceived
    }
  ),
  lifecycle<Props & ConnectState & ConnectDispatch, {}>({
    componentDidMount() {
      const {
        docRefInfoOpened,
        docRefInfoReceived,
        testDocRefWithInfo
      } = this.props;
      docRefInfoReceived(testDocRefWithInfo);
      docRefInfoOpened(testDocRefWithInfo.docRef);
    }
  })
);

const TestDocRefInfoModal = enhance(DocRefInfoModal);

storiesOf("Doc Ref/Info Modal", module)
  .addDecorator(StroomDecorator)
  .add("Doc Ref Info Modal", () => (
    <TestDocRefInfoModal
      testDocRefWithInfo={{
        docRef: {
          type: "Animal",
          name: "Tiger",
          uuid: "1234456789"
        },
        createTime: timeCreated,
        updateTime: Date.now(),
        createUser: "me",
        updateUser: "you",
        otherInfo: "I am test data"
      }}
    />
  ));
