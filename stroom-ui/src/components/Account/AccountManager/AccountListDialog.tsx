import * as React from "react";
import { Account } from "../types";
import { PeopleFill } from "react-bootstrap-icons";
import { ItemManagerProps } from "./ItemManager";
import { FunctionComponent } from "react";
import { ItemManagerDialog } from "./ItemManagerDialog";

export const AccountListDialog: FunctionComponent<{
  itemManagerProps: ItemManagerProps<Account>;
  onClose: () => void;
}> = ({ itemManagerProps, onClose }) => {
  return (
    <ItemManagerDialog<Account>
      title={
        <React.Fragment>
          <PeopleFill className="mr-3" />
          Manage Accounts
        </React.Fragment>
      }
      itemManagerProps={itemManagerProps}
      onClose={onClose}
    />
  );
};
