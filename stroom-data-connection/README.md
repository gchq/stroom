# stroom-data-connection

The Data Connection document type: one more kind of data source that StroomQL and dashboards can
query (gchq/stroom#6010). Its Type is JDBC or Amazon Athena, queried with the SQL in each query's `sql`
clause; LDAP, queried with the search filter in each query's `sql` clause; or CSV or an Excel workbook
held in the document, queried with Stroom's own clauses.

* [DESIGN.md](DESIGN.md) - the design and work plan.
* `DataConnection-Examples.zip` - an import pack for trying it out in dev or test:
  * a Data Connection pointed at Stroom's own MySQL database, with example StroomQL queries. Its
    set-up steps are on the Data Connection's Documentation tab after import;
  * a CSV Data Connection, Teams, and an Excel one, Bank Holidays, each with a query of it. They
    need no set-up;
  * a CSV Data Connection, People, and five "Join - ..." queries joining it to Teams and Bank
    Holidays: a left join, a keep join with a condition on the joined source, a drop join, a join to
    a bracketed grouped query, and a join on a date. They need no set-up either (see
    [JOINS_DESIGN.md](../stroom-query/JOINS_DESIGN.md));
  * an LDAP Data Connection, Directory, with example queries. Its set-up steps are on its
    Documentation tab after import.
