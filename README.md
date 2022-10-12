Cassandra ordering bug
===

This repository demonstrates what seems to be a bug with Cassandra 4.0 when
mixing Paxos with regular queries.

Assume a table `t` having a partition column `p`, a clustering column `c` and
a static column `s`. The following four queries are issued to it:

```sql
-- noinspection SqlResolveForFile, SqlNoDataSourceInspectionForFile
insert into t (p, s) values ('P', 'S1');
update t set s = 'S2' where p = 'P' if s = 'S1';
insert into t (p, s) values ('P', 'S3');
update t set s = 'S4' where p = 'P' if s = 'S3';
```

The expected result would be for all four queries to succeed (`applied: true`)
and for the table to contain at the end a single row with values `(P, 'S4', -)`
Instead, the first three queries report as applied and the last query reports
as not applied. The table contains a single row with values `(P, 'S2', -)`.
This explains why the fourth query doesn't apply, since the conditional failed.
It seems the queries are actually executed in Q1, Q3, Q2 order, which leads to
this result.

This bug can be avoided by introducing a delay between queries (>20ms).
