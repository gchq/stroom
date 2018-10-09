import * as React from "react";
import { compose } from "recompose";
import { withState } from "recompose";
import { storiesOf } from "@storybook/react";

import SelectBox, { Props as SelectBoxProps } from "./SelectBox";

import "../../styles/main.css";
import StroomDecorator from "../../lib/storybook/StroomDecorator";
import { addThemedStories } from "../../lib/themedStoryGenerator";
import { OptionType } from "../../types";

interface Props {
  options: Array<OptionType>;
}

const enhance = compose<SelectBoxProps, Props>(
  withState("value", "onChange", undefined)
);

const SelectBoxWrapped = enhance(
  ({ value, onChange, options }: SelectBoxProps) => (
    <SelectBox value={value} onChange={onChange} options={options} />
  )
);

const stories = storiesOf("Select Box", module).addDecorator(StroomDecorator);
addThemedStories(
  stories,
  <SelectBoxWrapped
    options={[1, 2, 3].map(o => ({
      text: `Option ${o}`,
      value: `option${o}`
    }))}
  />
);
