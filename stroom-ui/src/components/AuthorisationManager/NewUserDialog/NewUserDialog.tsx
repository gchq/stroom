import * as React from "react";

import { ThemedModal, DialogContent } from "components/ThemedModal";
import DialogActionButtons from "components/DialogActionButtons";
import NewUserForm, { useThisForm } from "./NewUserForm";

interface Props {
  isOpen: boolean;
  isGroup: boolean;
  onCreateUser: (name: string, isGroup: boolean) => void;
  onCloseDialog: () => void;
}

const NewUserDialog: React.FunctionComponent<Props> = ({
  isOpen,
  isGroup,
  onCreateUser,
  onCloseDialog,
}) => {
  const {
    value: { name },
    componentProps,
  } = useThisForm();

  const onConfirm = React.useCallback(() => {
    if (name) {
      onCreateUser(name, isGroup);
      onCloseDialog();
    }
  }, [onCreateUser, onCloseDialog, name, isGroup]);

  return (
    <ThemedModal isOpen={isOpen}>
      <DialogContent
        header={<h2>Create {isGroup ? "User" : "Group"}</h2>}
        content={<NewUserForm {...componentProps} />}
        actions={
          <DialogActionButtons onCancel={onCloseDialog} onConfirm={onConfirm} />
        }
      />
    </ThemedModal>
  );
};

interface UseDialog {
  componentProps: Props;
  showDialog: () => void;
}

interface UseDialogProps {
  isGroup: boolean;
  onCreateUser: (name: string, isGroup: boolean) => void;
}

export const useDialog = ({
  onCreateUser,
  isGroup,
}: UseDialogProps): UseDialog => {
  const [isOpen, setIsOpen] = React.useState<boolean>(false);

  return {
    componentProps: {
      isOpen,
      isGroup,
      onCreateUser,
      onCloseDialog: React.useCallback(() => {
        setIsOpen(false);
      }, [setIsOpen]),
    },
    showDialog: React.useCallback(() => {
      setIsOpen(true);
    }, [setIsOpen]),
  };
};

export default NewUserDialog;
