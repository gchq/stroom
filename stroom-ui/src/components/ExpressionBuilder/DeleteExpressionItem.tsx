import * as React from "react";
import { useState } from "react";

import { ThemedConfirm } from "../ThemedConfirm";

export interface Props {
  isOpen: boolean;
  onCloseDialog: () => void;
  expressionItemId?: string;
  onDeleteExpressionItem: (expressionItemId: string) => void;
}

const DeletePipelineExpressionItem = ({
  isOpen,
  onCloseDialog,
  expressionItemId,
  onDeleteExpressionItem
}: Props) => {
  return (
    <ThemedConfirm
      isOpen={isOpen}
      question={`Delete ${expressionItemId} from expression?`}
      onCloseDialog={onCloseDialog}
      onConfirm={() => {
        if (!!expressionItemId) {
          onDeleteExpressionItem(expressionItemId);
        }
        onCloseDialog();
      }}
    />
  );
};

export interface UseDialog {
  componentProps: Props;
  showDialog: (_expressionItemId: string) => void;
}

export const useDialog = (
  onDeleteExpressionItem: (e: string) => void
): UseDialog => {
  const [expressionItemId, setExpressionItemId] = useState<string | undefined>(
    undefined
  );
  const [isOpen, setIsOpen] = useState<boolean>(false);

  return {
    componentProps: {
      onDeleteExpressionItem,
      expressionItemId,
      isOpen,
      onCloseDialog: () => {
        setIsOpen(false);
        setExpressionItemId(undefined);
      }
    },
    showDialog: _expressionItemId => {
      setIsOpen(true);
      setExpressionItemId(_expressionItemId);
    }
  };
};

export default DeletePipelineExpressionItem;
