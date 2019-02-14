import * as React from "react";
import { useState } from "react";
import { storiesOf } from "@storybook/react";

import SingleValueWidget from "./SingleValueWidget";
import InValueWidget from "./InValueWidget";
import BetweenValueWidget from "./BetweenValueWidget";

import "../../../styles/main.css";

const stories = storiesOf("Expression/Value Widgets", module);

[
  { valueType: "text", defaultValue: "" },
  { valueType: "number", defaultValue: 10 },
  { valueType: "datetime-local", defaultValue: Date.now() }
].forEach(({ valueType, defaultValue }) => {
  stories
    .add(`Single ${valueType}`, () => {
      const B: React.FunctionComponent = () => {
        const [value, onChange] = useState(defaultValue);
        return (
          <div>
            <SingleValueWidget
              value={value}
              onChange={onChange}
              valueType={valueType}
            />
          </div>
        );
      };

      return <B />;
    })
    .add(`In ${valueType}`, () => {
      const B: React.FunctionComponent = () => {
        const [value, onChange] = useState(defaultValue);

        return (
          <div>
            <InValueWidget value={value} onChange={onChange} />
          </div>
        );
      };
      return <B />;
    })
    .add(`Between ${valueType}`, () => {
      const B: React.FunctionComponent = () => {
        const [value, onChange] = useState(defaultValue);

        return (
          <div>
            <BetweenValueWidget
              value={value}
              onChange={onChange}
              valueType={valueType}
            />
          </div>
        );
      };

      return <B />;
    });
});
