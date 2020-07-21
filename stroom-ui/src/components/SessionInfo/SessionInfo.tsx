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

import useBuildInfo from "./api/useSessionInfo";

export const SessionInfo: React.FunctionComponent = () => {
  const { userName, nodeName, buildInfo } = useBuildInfo();
  return (
    <div className="SessionInfo">
      <div>User Name: {userName}</div>
      <div>Build Version: {buildInfo.buildVersion}</div>
      <div>Build Date: {buildInfo.buildDate}</div>
      <div>Up Date: {buildInfo.upDate}</div>
      <div>Node Name: {nodeName}</div>
    </div>
  );
};
