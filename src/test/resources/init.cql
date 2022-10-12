-- noinspection SqlNoDataSourceInspectionForFile
create keyspace if not exists ks
with replication = { 'class': 'SimpleStrategy', 'replication_factor': 1 };

use ks;

create table if not exists t (
     k text,
     s text static,
     c text,
     primary key (k, c)
);
