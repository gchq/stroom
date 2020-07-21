import * as React from "react";

import { ThemedModal, DialogContent } from "components/ThemedModal";
import DialogActionButtons from "components/DialogActionButtons";
import IndexFieldTypePicker from "../IndexFieldTypePicker/IndexFieldTypePicker";
import AnalyzerPicker from "../AnalyzerPicker";
import useForm from "lib/useForm";
import {
  IndexField,
  IndexFieldType,
  AnalyzerType,
} from "components/DocumentEditors/useDocumentApi/types/indexDoc";

interface Props {
  id: number;
  indexField?: IndexField;
  onUpdateField: (id: number, updates: Partial<IndexField>) => void;
  onCloseDialog: () => void;
}

export const IndexFieldEditor: React.FunctionComponent<Props> = ({
  id,
  indexField,
  onUpdateField,
  onCloseDialog,
}) => {
  const {
    value: indexUpdates,
    useControlledInputProps,
    useTextInput,
    useCheckboxInput,
  } = useForm<IndexField>({
    initialValues: indexField,
  });

  const fieldNameProps = useTextInput("fieldName");
  const storedProps = useCheckboxInput("stored");
  const caseSensitiveProps = useCheckboxInput("caseSensitive");
  const termPositionsProps = useCheckboxInput("termPositions");
  const onConfirm = () => {
    onUpdateField(id, indexUpdates);
    onCloseDialog();
  };

  const fieldTypeProps = useControlledInputProps<IndexFieldType>("fieldType");
  const analyzerTypeProps = useControlledInputProps<AnalyzerType>(
    "analyzerType",
  );

  return !!indexField ? (
    <ThemedModal isOpen={!!indexField}>
      <DialogContent
        header={<h2>Index Field {indexField.fieldName}</h2>}
        content={
          <React.Fragment>
            <form>
              <label>Field Name</label>
              <input {...fieldNameProps} />
              <label>Field Type</label>
              <IndexFieldTypePicker {...fieldTypeProps} />
              <label>Stored</label>
              <input {...storedProps} />
              <label>Positions</label>
              <input {...termPositionsProps} />
              <label>Analyzer</label>
              <AnalyzerPicker {...analyzerTypeProps} />
              <label>Case Sensitive</label>
              <input {...caseSensitiveProps} />
            </form>
          </React.Fragment>
        }
        actions={
          <DialogActionButtons onConfirm={onConfirm} onCancel={onCloseDialog} />
        }
      />
    </ThemedModal>
  ) : null;
};

interface UseIndexFieldEditor {
  componentProps: Props;
  showEditor: (id: number, indexField: IndexField) => void;
}

export const useEditor = (
  onUpdateField: (id: number, updates: IndexField) => void,
): UseIndexFieldEditor => {
  const [id, setId] = React.useState<number>(0);
  const [indexField, setIndexField] = React.useState<IndexField | undefined>(
    undefined,
  );

  return {
    componentProps: {
      id,
      indexField,
      onUpdateField,
      onCloseDialog: React.useCallback(() => setIndexField(undefined), [
        setIndexField,
      ]),
    },
    showEditor: React.useCallback(
      (_id, _indexField) => {
        setId(_id);
        setIndexField(_indexField);
      },
      [setId, setIndexField],
    ),
  };
};

export default IndexFieldEditor;
