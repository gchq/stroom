import { storiesOf } from "@storybook/react";
import * as React from "react";
import { Table } from "./Table";
import { useMemo } from "react";
import makeData from "./makeData";
import styled from "styled-components";

const stories = storiesOf("Table", module);
stories.add("Table", () => {
  const columns = useMemo(
    () => [
      {
        Header: "First Name",
        accessor: "firstName",
        // sticky: "left",
      },
      {
        Header: "Last Name",
        accessor: "lastName",
      },
      {
        Header: "Age",
        accessor: "age",
        width: 50,
      },
      {
        Header: "Visits",
        accessor: "visits",
        width: 60,
      },
      {
        Header: "Status",
        accessor: "status",
      },
      {
        Header: "Profile Progress",
        accessor: "progress",
      },
    ],
    [],
  );

  const data = useMemo(() => makeData(1000), []);

  const Field = styled.div`
    position: relative;
    width: 400px;
    height: 400px;
    border: 1px solid red;
  `;

  return (
    <Field>
      <Table columns={columns} data={data} />
    </Field>
  );
});
