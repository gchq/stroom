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

import LineContext from "./LineContext";

import { actionCreators } from "./redux";
import { LineType } from "./types";

const { lineCreated, lineDestroyed } = actionCreators;

export interface Props extends LineType {
  lineId: string;
}

interface ConnectDispatch {
  lineCreated: typeof lineCreated;
  lineDestroyed: typeof lineDestroyed;
}

export interface ContextProps {
  lineContextId: string;
}

export interface EnhancedProps extends Props, ContextProps, ConnectDispatch {}

const enhance = connect(
  state => ({
    // operators are nested, so take all their props from parent
  }),
  {
    lineCreated,
    lineDestroyed
  }
);

class LineTo extends React.Component<EnhancedProps> {
  componentDidMount() {
    const {
      lineCreated,
      lineContextId,
      lineId,
      lineType,
      fromId,
      toId
    } = this.props;

    lineCreated(lineContextId, lineId, lineType, fromId, toId);
  }

  componentWillUnmount() {
    const { lineDestroyed, lineContextId, lineId } = this.props;
    lineDestroyed(lineContextId, lineId);
  }

  render() {
    return null;
  }
}

const EnhancedLineTo = enhance(LineTo);

export default (props: Props) => (
  <LineContext.Consumer>
    {lineContextId => (
      <EnhancedLineTo {...props} lineContextId={lineContextId} />
    )}
  </LineContext.Consumer>
);
