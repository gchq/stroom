import * as React from "react";
import { FunctionComponent } from "react";
import { ItemManagerProps } from "../AccountManager/ItemManager";
import { ApiKey } from "api/stroom";
import { ItemManagerDialog } from "../AccountManager/ItemManagerDialog";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";

export const TokenListDialog: FunctionComponent<{
  itemManagerProps: ItemManagerProps<ApiKey>;
  onClose: () => void;
}> = ({ itemManagerProps, onClose }) => {
  return (
    <ItemManagerDialog<ApiKey>
      title={
        <React.Fragment>
          <FontAwesomeIcon icon="key" className="mr-3" />
          Manage API Keys
        </React.Fragment>
      }
      itemManagerProps={itemManagerProps}
      onClose={onClose}
    />
  );
};
