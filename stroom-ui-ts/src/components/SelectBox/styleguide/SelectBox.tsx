import * as React from "react";
import { compose, withState } from "recompose";

import SelectBox, { Props as SelectBoxProps } from "../SelectBox";

const enhance = compose<SelectBoxProps, SelectBoxProps>(
  withState("value", "onChange", undefined)
);
const TestSelectBox = ({ value, onChange, options }: SelectBoxProps) => (
  <div>
    <SelectBox value={value} onChange={onChange} options={options} />
    <header>Observed Value</header>
    <div>{value}</div>
  </div>
);

export default enhance(TestSelectBox);
