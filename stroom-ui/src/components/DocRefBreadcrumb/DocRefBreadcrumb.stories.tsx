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
import { withState } from "recompose";

import { storiesOf } from "@storybook/react";

import StroomDecorator from "../../lib/storybook/StroomDecorator";
import DocRefBreadcrumb from "./DocRefBreadcrumb";

import { DocRefType, DocRefConsumer } from "../../types";
import { testPipelines } from "../PipelineEditor/test";

import "../../styles/main.css";

interface Props {
  docRefUuid: string;
}
interface WithOpenDocRef {
  openDocRef: DocRefType;
  setOpenDocRef: DocRefConsumer;
}
interface EnhancedProps extends Props, WithOpenDocRef {}

const enhance = compose<EnhancedProps, Props>(
  withState("openDocRef", "setOpenDocRef", undefined)
);

const BreadcrumbOpen = enhance(
  ({ docRefUuid, openDocRef, setOpenDocRef }: EnhancedProps) => (
    <div>
      <div>Doc Ref Breadcrumb</div>
      <DocRefBreadcrumb docRefUuid={docRefUuid} openDocRef={setOpenDocRef} />
      <div>{JSON.stringify(openDocRef)}</div>
    </div>
  )
);

const testPipelineUuid = Object.keys(testPipelines)[0];

storiesOf("Doc Ref Breadcrumb", module)
  .addDecorator(StroomDecorator)
  .add("first pipeline", () => (
    <BreadcrumbOpen docRefUuid={testPipelineUuid} />
  ));
