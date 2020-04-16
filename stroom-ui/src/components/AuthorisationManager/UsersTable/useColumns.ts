import { Column } from "react-table";
import { StroomUser } from "../api/userGroups";

const useColumns = (): Column<StroomUser>[] => {
  const columns: Column<StroomUser>[] = [
    {
      id: "uuid",
      Header: "UUID",
      accessor: (u: StroomUser) => u.uuid,
    },
    {
      id: "name",
      Header: "Name",
      accessor: (u: StroomUser) => u.name,
    },
    {
      id: "isGroup",
      Header: "Is Group",
      accessor: (u: StroomUser) => (u.group ? "Group" : "User"),
      filterable: false,
    },
  ];

  return columns;
};

export default useColumns;
