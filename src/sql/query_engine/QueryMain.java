/*
 * Author	-	@Nagaraj Poti
 * Roll 	-	20162010
 */
package sql.query_engine;

import java.io.IOException;

class QueryMain {

	public static void main(String[] args) throws IOException {
		
		/* Read sql statement from the command line */
		String sqlQuery = "";
		for (int i = 0; i < args.length - 1; i++) {
			sqlQuery += args[i] + " ";
		}
		sqlQuery += args[args.length - 1];

		QueryEngine q = new QueryEngine(sqlQuery);
		q.parseQuery();
		q.executeQuery();
		return;
	}
	
}
