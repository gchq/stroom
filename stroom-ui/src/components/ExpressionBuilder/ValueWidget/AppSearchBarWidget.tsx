import AppSearchBar from "components/AppSearchBar";
import Button from "components/Button";
import { DocRefType } from "components/DocumentEditors/useDocumentApi/types/base";
import { ControlledInput } from "lib/useForm/types";
import * as React from "react";
import { FunctionComponent, useCallback } from "react";

interface Props extends ControlledInput<any> {
  onChange: any;
  value: DocRefType;
}

const AppSearchBarWidget: FunctionComponent<Props> = ({ onChange, value }) => {
  const [isEditing, setEditing] = React.useState(false);
  const handleChange = useCallback(
    (value) => {
      onChange(value);
      setEditing(false);
    },
    [onChange],
  );
  if (isEditing) {
    return (
      <div className="AppSearchBarWidget__container">
        <AppSearchBar
          typeFilter="Dictionary"
          onChange={handleChange}
          value={value}
        />

        <Button
          appearance="contained"
          action="secondary"
          icon="times"
          text="Cancel"
          onClick={() => setEditing(false)}
        />
      </div>
    );
  } else {
    const textToDisplay = !!value ? value.name : "click to select";
    return <span onClick={() => setEditing(true)}>{textToDisplay}</span>;
  }
};

export default AppSearchBarWidget;
