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
import { storiesOf } from "@storybook/react";

import useSelectableReactTable from "./useSelectableReactTable";
import { SelectionBehaviour } from "./enums";
import ReactTable from "react-table";
import useForm from "../useForm";
import Button from "components/Button";
import JsonDebug from "testing/JsonDebug";
import useTestAnimals, { Animal } from "./useTestAnimals";

const COLUMNS = [
  {
    id: "species",
    Header: "Species",
    accessor: (u: Animal) => u.species,
  },
  {
    id: "name",
    Header: "Name",
    accessor: (u: Animal) => u.name,
  },
];

interface NewItemFormValues {
  species: string;
  name: string;
}

const defaultFormValues: NewItemFormValues = {
  species: "Dog",
  name: "Fluffy",
};

const TestTable = () => {
  const {
    value: { species, name },
    useTextInput,
  } = useForm<NewItemFormValues>({ initialValues: defaultFormValues });

  const { animals, preFocusWrap, reset, addAnimal } = useTestAnimals();

  const speciesProps = useTextInput("species");
  const nameProps = useTextInput("name");
  const onClickAddItem = React.useCallback(
    (e) => {
      if (!!name && !!species) {
        addAnimal(species, name);
      }
      e.preventDefault();
    },
    [name, species, addAnimal],
  );

  const { onKeyDown, selectedItem, tableProps } = useSelectableReactTable<
    Animal
  >(
    {
      getKey: React.useCallback((a) => a.uuid, []),
      items: animals,
      selectionBehaviour: SelectionBehaviour.MULTIPLE,
      preFocusWrap,
    },
    {
      columns: COLUMNS,
    },
  );

  return (
    <div className="fill-space" tabIndex={0} onKeyDown={onKeyDown}>
      <Button onClick={reset}>Reset</Button>
      <ReactTable
        className="useSelectableReactTable -striped -highlight"
        {...tableProps}
      />
      <form>
        <label>Species</label>
        <input {...speciesProps} />
        <label>Name</label>
        <input {...nameProps} />

        <Button onClick={onClickAddItem}>Add Item</Button>
      </form>

      <JsonDebug
        value={{
          species: speciesProps.value,
          name: nameProps.value,
          selectedItem,
        }}
      />
    </div>
  );
};

storiesOf("lib/useSelectableReactTable", module).add("Table", TestTable);
