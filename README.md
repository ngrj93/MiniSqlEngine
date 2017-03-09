# MiniSqlEngine
Mini Sql Engine - Handles Select, Aggregate, Project, Join Operations 

1. Select all records :
        Select * from table_name;
        
2. Aggregate functions : Simple aggregate functions on a single column. Sum, average, max and min. 
        select max(col1) from table1;
        
3. Project Columns (could be any number of columns) from one or more tables : 
        Select col1, col2 from table_name;
        
4. Select/project with distinct from one table : 
        select distinct col1, col2 from table_name;
        
5. Select with where from one or more tables : select col1,col2 from table1,table2 where col1 = 10 AND col2 = 20;
        a. In the where queries, multiple AND/OR operators are allowed with no NOT operators.
        
6. Projection of one or more(including all the columns) from two tables with one join condition :
        a. select * from table1, table2 where table1.col1=table2.col2;
        b. select col1,col2 from table1,table2 where table1.col1 = table2.col2;
        
Misc

csv files for tables
