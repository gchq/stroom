import { storiesOf } from "@storybook/react";
import * as React from "react";
import { Table } from "./Table";
import { useMemo } from "react";
import makeData from "./makeData";

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

  const data = useMemo(() => makeData(20), []);

  return <Table columns={columns} data={data} />;
});
