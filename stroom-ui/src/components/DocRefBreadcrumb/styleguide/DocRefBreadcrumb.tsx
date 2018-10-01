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
import { compose, withState } from "recompose";

import DocRefBreadcrumb from "../DocRefBreadcrumb";
import { DocRefConsumer, DocRefType } from "../../../types";

const withOpenDocRef = withState("openDocRef", "setOpenDocRef", undefined);

export interface Props {
  docRefUuid: string;
}

interface StateProps {
  openDocRef: DocRefType;
  setOpenDocRef: DocRefConsumer;
}

export interface EnhancedProps extends Props, StateProps {}

const enhance = compose<EnhancedProps, Props>(withOpenDocRef);

const BreadcrumbOpen = ({
  docRefUuid,
  openDocRef,
  setOpenDocRef
}: EnhancedProps) => (
  <div>
    <div>Doc Ref Breadcrumb</div>
    <DocRefBreadcrumb docRefUuid={docRefUuid} openDocRef={setOpenDocRef} />
    <div>{JSON.stringify(openDocRef)}</div>
  </div>
);

export default enhance(BreadcrumbOpen);
