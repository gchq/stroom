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

import { LineContainer, LineTo } from ".";

import Curve from "./lineCreators/Curve";
import ElbowDown from "./lineCreators/ElbowDown";
import LineEndpoint from "./LineEndpoint";

const testBlockStyle: React.CSSProperties = {
  position: "absolute",
  width: "50px",
  border: "solid thin black",
};

const DefaultLineTest: React.FunctionComponent = () => {
  return (
    <div>
      <LineContainer>
        <h1>Some stuff</h1>
        <p>This is some actual content for this page</p>
        <LineEndpoint
          lineEndpointId="myFirst"
          style={{
            ...testBlockStyle,
            top: "50px",
            left: "50px",
          }}
        >
          From
        </LineEndpoint>
        <LineEndpoint
          lineEndpointId="mySecond"
          style={{
            ...testBlockStyle,
            top: "250px",
            left: "150px",
          }}
        >
          To
        </LineEndpoint>
        <LineTo fromId="myFirst" toId="mySecond" />
      </LineContainer>
    </div>
  );
};

const CurveTest: React.FunctionComponent = () => {
  return (
    <div>
      <LineContainer LineElementCreator={Curve}>
        <LineEndpoint
          lineEndpointId="myFirst"
          style={{
            ...testBlockStyle,
            top: "50px",
            left: "150px",
          }}
        >
          From
        </LineEndpoint>
        <LineEndpoint
          lineEndpointId="mySecond"
          style={{
            ...testBlockStyle,
            top: "250px",
            left: "50px",
          }}
        >
          Mid1
        </LineEndpoint>
        <LineEndpoint
          lineEndpointId="myThird"
          style={{
            ...testBlockStyle,
            top: "150px",
            left: "350px",
          }}
        >
          End
        </LineEndpoint>
        <LineTo fromId="myFirst" toId="mySecond" />
        <LineTo fromId="mySecond" toId="myThird" />
      </LineContainer>
    </div>
  );
};

const ElbowDownTest: React.FunctionComponent = () => {
  return (
    <div>
      <LineContainer LineElementCreator={ElbowDown}>
        <LineEndpoint
          lineEndpointId="myFirst"
          style={{
            ...testBlockStyle,
            top: "50px",
            left: "50px",
          }}
        >
          From
        </LineEndpoint>
        <LineEndpoint
          lineEndpointId="mySecond"
          style={{
            ...testBlockStyle,
            top: "250px",
            left: "150px",
          }}
        >
          To
        </LineEndpoint>
        <LineTo fromId="myFirst" toId="mySecond" />
      </LineContainer>
    </div>
  );
};

const multiBlockStyle: React.CSSProperties = {
  display: "flex",
  width: "100%",
  height: "100%",
};
const multiBlockPartStyle: React.CSSProperties = {
  borderStyle: "solid",
  width: "100%",
  height: "100%",
};

const MultipleLineContainersTest: React.FunctionComponent = () => (
  <div>
    <h2>This tests is the relative positions are calculated correctly</h2>
    <div style={multiBlockStyle}>
      <div style={{ ...multiBlockPartStyle, borderColor: "lime" }}>
        LINE
        <DefaultLineTest />
      </div>
      <div style={{ ...multiBlockPartStyle, borderColor: "lightblue" }}>
        CURVE
        <CurveTest />
      </div>
      <div style={{ ...multiBlockPartStyle, borderColor: "yellow" }}>
        ELBOW
        <ElbowDownTest />
      </div>
    </div>
  </div>
);

storiesOf("General Purpose/Line To SVG", module)
  .add("Default Line", () => <DefaultLineTest />)
  .add("Elbow Down", () => <ElbowDownTest />)
  .add("Curve", () => <CurveTest />)
  .add("Multiple", () => <MultipleLineContainersTest />);
