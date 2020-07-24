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

import { storiesOf } from "@storybook/react";
import * as React from "react";
import Button from "./Button";

interface Variant {
  title: string;
  appearance: "default" | "outline" | "icon" | "contained";
}
const variants: Variant[] = [
  {
    title: "Default Buttons",
    appearance: "default",
  },
  {
    title: "Outline Buttons",
    appearance: "outline",
  },
  {
    title: "Icon Buttons",
    appearance: "icon",
  },
  {
    title: "Contained Buttons",
    appearance: "contained",
  },
];

const styles = {
  appearanceContainer: {
    display: "grid",
    gridTemplateColumns: "100px 200px 200px 200px 200px",
  },
  appearanceHeader: {
    gridColumnStart: "1",
    gridColumnEnd: "4",
  },
  propertyHeader: {
    gridColumnStart: "2",
    gridColumnEnd: "3",
  },
  buttonContainer: {
    gridColumnStart: "3",
  },
  firstButton: {
    gridColumnStart: "3",
    gridColumnEnd: "4",
  },
  secondButton: {
    gridColumnStart: "4",
    gridColumnEnd: "5",
  },
  thirdButton: {
    gridColumnStart: "5",
    gridColumnEnd: "6",
  },
};

const TestHarness: React.FunctionComponent = () => {
  return (
    <div>
      {variants.map(({ appearance }, i) => (
        <div key={i} style={styles.appearanceContainer}>
          <h3 style={styles.appearanceHeader}>
            appearance=&#34;{appearance}&#34;
          </h3>
          <b style={styles.propertyHeader}>Simple</b>
          <Button
            style={styles.firstButton}
            appearance={appearance}
            action="primary"
            icon="save"
            title="Save"
          >
            Save
          </Button>
          <Button
            style={styles.secondButton}
            appearance={appearance}
            action="secondary"
            icon="times"
            title="Close"
          >
            Close
          </Button>
          <Button
            style={styles.thirdButton}
            appearance={appearance}
            icon="key"
            title="Permissions"
          >
            Permissions
          </Button>

          <b style={styles.propertyHeader}>selected=&#123;true&#125;</b>
          <Button
            style={styles.firstButton}
            appearance={appearance}
            action="primary"
            icon="save"
            title="Save"
            selected={true}
          >
            Save
          </Button>
          <Button
            style={styles.secondButton}
            appearance={appearance}
            action="secondary"
            icon="times"
            title="Close"
            selected={true}
          >
            Close
          </Button>
          <Button
            style={styles.thirdButton}
            appearance={appearance}
            icon="key"
            title="Permissions"
            selected={true}
          >
            Permissions
          </Button>

          <b style={styles.propertyHeader}>disabled=&#123;true&#125;</b>
          <Button
            style={styles.firstButton}
            appearance={appearance}
            action="primary"
            icon="save"
            title="Save"
            disabled={true}
          >
            Save
          </Button>
          <Button
            style={styles.secondButton}
            appearance={appearance}
            action="secondary"
            icon="times"
            title="Close"
            disabled={true}
          >
            Close
          </Button>
          <Button
            style={styles.thirdButton}
            appearance={appearance}
            icon="key"
            title="Permissions"
            disabled={true}
          >
            Permissions
          </Button>

          <b style={styles.propertyHeader}>size=&#34;small&#34;</b>
          <Button
            style={styles.firstButton}
            size="small"
            appearance={appearance}
            action="primary"
            icon="save"
            title="Save"
          >
            Save
          </Button>
          <Button
            style={styles.secondButton}
            size="small"
            appearance={appearance}
            action="secondary"
            icon="times"
            title="Close"
          >
            Close
          </Button>
          <Button
            style={styles.thirdButton}
            size="small"
            appearance={appearance}
            icon="key"
            title="Permissions"
          >
            Permissions
          </Button>

          <b style={styles.propertyHeader}>size=&#34;large&#34;</b>
          <Button
            style={styles.firstButton}
            size="large"
            appearance={appearance}
            action="primary"
            icon="save"
            title="Save"
          >
            Save
          </Button>
          <Button
            style={styles.secondButton}
            size="large"
            appearance={appearance}
            action="secondary"
            icon="times"
            title="Close"
          >
            Close
          </Button>
          <Button
            style={styles.thirdButton}
            size="large"
            appearance={appearance}
            icon="key"
            title="Permissions"
          >
            Permissions
          </Button>
        </div>
      ))}
    </div>
  );
};

storiesOf("General Purpose", module).add("Button", () => <TestHarness />);
