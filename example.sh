#!/bin/bash

cd ./bin
java -cp .:./* sql.query_engine.QueryMain "select table1.A, table1.B from table1 where table1.A = 640"
