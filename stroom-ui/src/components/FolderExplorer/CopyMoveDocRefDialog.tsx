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
import * as React from "react";
import { useState } from "react";

import { Formik, Field, FieldProps } from "formik";

import IconHeader from "../IconHeader";
import DialogActionButtons from "./DialogActionButtons";
import ThemedModal from "../ThemedModal";
import AppSearchBar from "../AppSearchBar";
import PermissionInheritancePicker from "../PermissionInheritancePicker";
import { PermissionInheritance, DocRefType } from "../../types";

export interface Props {
  uuids: Array<string>;
  destinationUuid?: string;
  isOpen: boolean;
  onConfirm: (
    uuids: Array<string>,
    destinationUuid: string,
    permissionInheritance: PermissionInheritance
  ) => void;
  onCloseDialog: () => void;
}

interface FormValues {
  destination?: DocRefType;
  permissionInheritance: PermissionInheritance;
}

let CopyMoveDocRefDialog = ({
  uuids,
  destinationUuid,
  isOpen,
  onConfirm,
  onCloseDialog
}: Props) => (
  <Formik<FormValues>
    initialValues={{
      destination: undefined, // TODO - fix initial value
      permissionInheritance: PermissionInheritance.NONE
    }}
    onSubmit={values => {
      if (!!values.destination) {
        onConfirm(
          uuids,
          values.destination!.uuid,
          values.permissionInheritance
        );
      }
      onCloseDialog();
    }}
  >
    {({ setFieldValue, submitForm }: Formik) => (
      <ThemedModal
        isOpen={isOpen}
        header={
          <IconHeader
            icon="copy"
            text="Select a Destination Folder for the Copy"
          />
        }
        content={
          <form>
            <div>
              <label>Destination</label>
              <Field name="destination">
                {({ field: { value } }: FieldProps) => (
                  <AppSearchBar
                    pickerId="copy-dialog"
                    onChange={d => setFieldValue("destination", d)}
                    value={value}
                    typeFilters={[]}
                  />
                )}
              </Field>
            </div>
            <div>
              <label>Permission Inheritance</label>
              <Field name="permissionInheritance">
                {({ field: { value } }: FieldProps) => (
                  <PermissionInheritancePicker
                    onChange={d => setFieldValue("permissionInheritance", d)}
                    value={value}
                  />
                )}
              </Field>
            </div>
          </form>
        }
        actions={
          <DialogActionButtons
            onCancel={onCloseDialog}
            onConfirm={submitForm}
          />
        }
      />
    )}
  </Formik>
);

export type ShowCopyDocRefDialog = (
  uuids: Array<string>,
  destinationUuid?: string
) => void;

export type UseCopyDocRefDialog = {
  showDialog: ShowCopyDocRefDialog;
  componentProps: Props;
};

export const useCopyMoveDocRefDialog = (
  onConfirm: (
    uuids: Array<string>,
    destinationUuid: string,
    permissionInheritance: PermissionInheritance
  ) => void
): UseCopyDocRefDialog => {
  const [destinationUuid, setDestinationUuid] = useState<string | undefined>(
    undefined
  );
  const [uuidsToCopy, setUuidToCopy] = useState<Array<string>>([]);
  const [isOpen, setIsOpen] = useState<boolean>(false);

  return {
    componentProps: {
      onConfirm,
      uuids: uuidsToCopy,
      destinationUuid,
      isOpen,
      onCloseDialog: () => {
        setIsOpen(false);
        setUuidToCopy([]);
        setDestinationUuid(undefined);
      }
    },
    showDialog: (_uuids, _destinationUuid) => {
      setIsOpen(true);
      setUuidToCopy(_uuids);
      setDestinationUuid(_destinationUuid);
    }
  };
};

export default CopyMoveDocRefDialog;
