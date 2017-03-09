/*
 * Author	-	@Nagaraj Poti
 * Roll 	-	20162010
 */
package sql.query_engine;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.*;
import gudusoft.gsqlparser.*;
import gudusoft.gsqlparser.nodes.TJoin;
import gudusoft.gsqlparser.nodes.TResultColumn;
import gudusoft.gsqlparser.stmt.*;

class QueryEngine {
	
	private String metadataFile = "metadata.txt";
	private String sqlQuery;
	private ArrayList<String> selectTables;
	private ArrayList<Map.Entry<String,String>> selectFields;
	private String whereClause;
	private ArrayList<String> operations;
	private HashMap<String, Set<String>> schema;
	private HashMap<String, ArrayList<ArrayList<Integer>>> tableInfo;
	private ArrayList<HashMap<Map.Entry<String, String>, Integer>> result;
	private int isDistinct;
	private ArrayList<String> aggregators;
	private int isStar;
		
	public QueryEngine(String sqlQuery) {
		this.sqlQuery = sqlQuery;
		selectTables = new ArrayList<String>();
		selectFields = new ArrayList<Map.Entry<String, String>>();
		whereClause = null;
		operations = new ArrayList<String>();
		schema = new HashMap<String, Set<String>>();
		tableInfo = new HashMap<String, ArrayList<ArrayList<Integer>>>();
		result = new ArrayList<HashMap<Map.Entry<String, String>, Integer>>();
		aggregators = new ArrayList<String>();
		isDistinct = 1;
		isStar = 0;
	}
	
	String parseQuery() {
		TGSqlParser sqlParser = new TGSqlParser(EDbVendor.dbvmysql);
		sqlParser.sqltext = sqlQuery;
		/* Presence of distinct keyword */
	    String pattern = "(?i)(.*)(distinct)(.*)";
	    Pattern p = Pattern.compile(pattern);  
	    Matcher m = p.matcher(sqlQuery);
	    int count = 0;
	    while(m.find()) {
	    	count++;
	    	isDistinct = 1;
	    }
	    if (count > 1) {
	    	System.out.println("Error : multiple distinct keywords detected!");
	    	System.exit(0);
	    }
	    pattern = "(?i)(.*)(distinct) *\\(.*,.*\\)(.*)";
	    p = Pattern.compile(pattern);
	    m = p.matcher(sqlQuery);
	    if (m.find()) {
	    	System.out.println("Error : distinct keyword incorrect usage!");
	    	System.exit(0);
	    }
		int success = sqlParser.parse();
		if (success == 0) {
			try {
				processStmt((TSelectSqlStatement)sqlParser.sqlstatements.get(0));				
			} catch (Exception e) {
				System.out.println("Error: Query statement not supported! Only select statement allowed!");
				System.exit(0);
			}
		}
		else {
			System.out.println("Error: Query syntax error! Please check again!");
			System.exit(0);
		}
		return "";
	}
	
	/* Process the select statement to identify and store individual tokens */
	void processStmt(TSelectSqlStatement query) {
		/* Fetch join tables from the query statement */
		int tableCount = query.joins.size();
		for (int i = 0; i < tableCount; i++) {
			TJoin join = query.joins.getJoin(i);
			String tableName = join.getTable().toString();
			checkTableExists(tableName);
			selectTables.add(tableName); 
		}
		/* Fetch columns and their respective aliases from the query statement */
		int columnCount = query.getResultColumnList().size(), pos = -1;
		for (int i = 0; i < columnCount; i++) {
			TResultColumn result = query.getResultColumnList().getResultColumn(i);
			String fieldName = result.getExpr().toString();
			if (fieldName.equals("*")) {
				isStar = 1;
			}
			fieldName = checkAggregator(fieldName);			
			String fieldTable = null;
			if ((pos = fieldName.indexOf('.')) != -1) {
				fieldTable = strip(fieldName.substring(0, pos));
				fieldName = strip(fieldName.substring(pos + 1));
			}
			selectFields.add(new CustomEntry<String, String>(fieldName, fieldTable));
		}		
		/* Fetch where clause entities from the query statement */
		if (query.getWhereClause() != null) {
			whereClause = query.getWhereClause().getCondition().toString();
			operations = generatePrecedence(whereClause);
		}
	}
	
	String checkAggregator(String field) {
		String p1 = "(?i)(.*)(max *)(\\(.+?\\))(.*)";
		String p2 = "(?i)(.*)(min *)(\\(.+?\\))(.*)";
		String p3 = "(?i)(.*)(avg *)(\\(.+?\\))(.*)";
		String p4 = "(?i)(.*)(sum *)(\\(.+?\\))(.*)";
		Pattern pp1 = Pattern.compile(p1);  
		Pattern pp2 = Pattern.compile(p2);  
		Pattern pp3 = Pattern.compile(p3);  
		Pattern pp4 = Pattern.compile(p4);  
		Matcher m = pp1.matcher(field);
		while (m.find()) {
			String fieldName = m.group(3).substring(1, m.group(3).length() - 1);
			String fieldTable = " ";
			int pos = -1;
			if ((pos = fieldName.indexOf('.')) != -1) {
				fieldTable = strip(fieldName.substring(0, pos));
				fieldName = strip(fieldName.substring(pos + 1));
			}
			aggregators.add("max" + ":" + fieldTable + ":" + fieldName);
			field = m.group(3).substring(1, m.group(3).length() - 1);
		}
		m = pp2.matcher(field);
		while (m.find()) {
			String fieldName = m.group(3).substring(1, m.group(3).length() - 1);
			String fieldTable = " ";
			int pos = -1;
			if ((pos = fieldName.indexOf('.')) != -1) {
				fieldTable = strip(fieldName.substring(0, pos));
				fieldName = strip(fieldName.substring(pos + 1));
			}
			aggregators.add("min" + ":" + fieldTable + ":" + fieldName);
			field = m.group(3).substring(1, m.group(3).length() - 1);
		}
		m = pp3.matcher(field);
		while (m.find()) {
			String fieldName = m.group(3).substring(1, m.group(3).length() - 1);
			String fieldTable = " ";
			int pos = -1;
			if ((pos = fieldName.indexOf('.')) != -1) {
				fieldTable = strip(fieldName.substring(0, pos));
				fieldName = strip(fieldName.substring(pos + 1));
			}
			aggregators.add("avg" + ":" + fieldTable + ":" + fieldName);
			field = m.group(3).substring(1, m.group(3).length() - 1);
		}
		m = pp4.matcher(field);
		while (m.find()) {
			String fieldName = m.group(3).substring(1, m.group(3).length() - 1);
			String fieldTable = " ";
			int pos = -1;
			if ((pos = fieldName.indexOf('.')) != -1) {
				fieldTable = strip(fieldName.substring(0, pos));
				fieldName = strip(fieldName.substring(pos + 1));
			}
			aggregators.add("sum" + ":" + fieldTable + ":" + fieldName);
			field = m.group(3).substring(1, m.group(3).length() - 1);
		}
		return field;
	}
	
	/* Generate postfix expression to evaluate where clause conditions */
	ArrayList<String> generatePrecedence(String clause) {
		ArrayList<String> result = new ArrayList<String>();
		String or = " or ", and = " and ";
		Stack<Integer> reversePolish = new Stack<Integer>();
		int curPos = 0, orPos = clause.indexOf(or, curPos), andPos = clause.indexOf(and,  curPos);
		int newElement;
		while (orPos != -1 && andPos != -1) {
			if(orPos < andPos) {
				result.add(strip(clause.substring(curPos, orPos)));
				newElement = 0;
			}
			else {
				result.add(strip(clause.substring(curPos, andPos)));
				newElement = 1;
			}
			curPos = (newElement == 1 ? andPos + and.length() : orPos + or.length());
			while (!reversePolish.isEmpty() && reversePolish.peek() > newElement) {
				reversePolish.pop();
				result.add("and");
			}
			reversePolish.push(newElement);
			orPos = clause.indexOf(or, curPos);
			andPos = clause.indexOf(and, curPos);
		}
		while (orPos != - 1) {
			result.add(strip(clause.substring(curPos, orPos)));
			newElement = 0;
			curPos = orPos + or.length();
			while (!reversePolish.isEmpty() && reversePolish.peek() > newElement) {
				reversePolish.pop();
				result.add("and");
			}
			reversePolish.push(newElement);
			orPos = clause.indexOf(or, curPos);
		}
		while (andPos != - 1) {
			result.add(strip(clause.substring(curPos, andPos)));
			newElement = 1;
			reversePolish.push(newElement);
			curPos = andPos + and.length();
			andPos = clause.indexOf(and, curPos);
		}
		result.add(strip(clause.substring(curPos)));
		while (!reversePolish.isEmpty()) {
			int temp = reversePolish.peek();
			reversePolish.pop();
			if (temp == 1) result.add("and"); else result.add("or");
		}
		return result;
	}
	
	/* Execute the query and fetch records satisfying selection conditions */
	void executeQuery() throws IOException {
		populateMetadata();
		populateTables();
		/* Fetch column names from the operations */
		HashMap<Map.Entry<String, String>, Integer> record = new LinkedHashMap<Map.Entry<String, String>, Integer>();		
		int recordCount = populateResult(record, selectTables.size(), 0);
		if (recordCount == 0) {
			System.out.println("No records found!");
		}
		else {
			display();
		}
	}
	
	void populateMetadata() throws IOException {
		File f = new File(metadataFile);
		try {
			BufferedReader in = new BufferedReader(new FileReader(f));
			String line = in.readLine();
			while (line != null && !line.isEmpty()) {
				if (line.indexOf("<begin_table>") != -1) {
					String tableName = strip(in.readLine());
					schema.put(tableName, new LinkedHashSet<String>());
					line = strip(in.readLine());
					while (line.indexOf("<end_table>") == -1) {
						if (schema.get(tableName).contains(line)) {
							System.out.println("Error : Duplicate field name entries in the same table!");
							System.exit(0);
						}
						schema.get(tableName).add(line);
						line = strip(in.readLine());
					}
				}
				else {
					System.out.println("Error : Incorrect format of metadata file! May be caused due to erroneous spacing/newlines!");
					System.exit(0);
				}
				line = strip(in.readLine());
			}
			in.close();
		} catch (FileNotFoundException e) {
			System.out.println("Error : Metadata file not found! Please ensure it is visible to the program!");
		}
	}
	
	void populateTables() throws IOException {
		for (int i = 0; i < selectTables.size(); i++) {
			File f = new File(selectTables.get(i) + ".csv");
			BufferedReader in = new BufferedReader(new FileReader(f));
			tableInfo.put(selectTables.get(i), new ArrayList<ArrayList<Integer>>());
			String line = strip(in.readLine());
			while (line != null && !line.isEmpty()) {
				ArrayList<Integer> temp = new ArrayList<Integer>();
				String[] values = line.split(",");
				for (int j = 0; j < values.length; j++) {
					values[j] = values[j].replaceAll("\"","");
					values[j] = values[j].replaceAll("\'","");
					temp.add(Integer.parseInt(values[j]));
				}
				tableInfo.get(selectTables.get(i)).add(temp);
				line = strip(in.readLine());
			}
			in.close();
		}
	}
	
	int populateResult(HashMap<Map.Entry<String, String>, Integer> record, int maxBound, int curPos) {
		if (curPos == maxBound) {
			if (evaluateRecord(record) == true) {
				result.add(record);
				return 1;
			}
			return 0;
		}
		String tableName = selectTables.get(curPos);
		ArrayList<ArrayList<Integer>> currentTable = tableInfo.get(tableName);
		int resultCount = 0;
		for (int i = 0; i < currentTable.size(); i++) {
			HashMap<Map.Entry<String, String>, Integer> newRecord = new LinkedHashMap<Map.Entry<String, String>, Integer>(record);
			Iterator<String> it = schema.get(tableName).iterator();
			int k = 0;
			while (it.hasNext()) {
				String fieldName = (String)it.next();
				CustomEntry<String, String> newEntry = new CustomEntry<String, String>(tableName, fieldName);
				newRecord.put(newEntry, currentTable.get(i).get(k));
				k++;
			}
			resultCount += populateResult(newRecord, maxBound, curPos + 1);
		}
		return resultCount;
	}

	boolean evaluateRecord(HashMap<Map.Entry<String, String>, Integer> record) {
		try {
			Stack<Integer> evalStack = new Stack<Integer>();
			for (int i = 0; i < operations.size(); i++) {			
				if (operations.get(i) == "or" || operations.get(i) == "and") {
					int secondOperand = evalStack.peek();
					evalStack.pop();
					int firstOperand = evalStack.peek();
					evalStack.pop();
					if (operations.get(i) == "or") {
						evalStack.push(firstOperand | secondOperand);
					}
					else if (operations.get(i) == "and") { 
						evalStack.push(firstOperand & secondOperand);
					}
				}
				else {
					String condition = operations.get(i);
					int operation = -1;
					String[] parts = null;
					if (condition.indexOf("!=") != -1) {
						parts = condition.split("!=");	operation = 0;
					}
					else if (condition.indexOf("<>") != -1) {
						parts = condition.split("<>"); operation = 1;
					}
					else if (condition.indexOf("<=") != -1) {
						parts = condition.split("<="); operation = 2;
					}
					else if (condition.indexOf("<") != -1) {
						parts = condition.split("<"); operation = 3;
					}
					else if (condition.indexOf(">=") != -1) {
						parts = condition.split(">="); operation = 4;
					}
					else if (condition.indexOf(">") != -1) {
						parts = condition.split(">"); operation = 5;
					}
					else if (condition.indexOf('=') != -1) {
						parts = condition.split("="); operation = 6;
					}
					else {
						System.out.println("Error : Invalid operators or not supported! Choose one of =,!=,<>,<=,<,>=,> only!");
						System.exit(0);
					}
					Map.Entry<String, String> firstField = findField(parts[0]);
					Map.Entry<String, String> secondField = findField(parts[1]);			
					if (firstField.getKey().equals("pure_integer")) {
						if (secondField.getKey().equals("pure_integer")) {
							Integer a = Integer.parseInt(firstField.getValue());
							Integer b = Integer.parseInt(secondField.getValue());
							if (operate(a, b, operation)) {
								evalStack.push(1);
							}
							else {
								evalStack.push(0);
							}
						}
						else {
							Integer a = Integer.parseInt(firstField.getValue());
							Integer b = findInRecord(secondField, record);
							if (operate(a, b, operation)) {
								evalStack.push(1);
							}
							else {
								evalStack.push(0);
							}
						}
					}
					else {
						if (secondField.getKey().equals("pure_integer")) {
							Integer a = findInRecord(firstField, record);
							Integer b = Integer.parseInt(secondField.getValue());							
							if (operate(a, b, operation)) {
								evalStack.push(1);
							}
							else {
								evalStack.push(0);
							}
						}
						else {
							Integer a = findInRecord(firstField, record);
							Integer b = findInRecord(secondField, record);							
							if (operate(a, b, operation)) {
								evalStack.push(1);
							}
							else {
								evalStack.push(0);
							}
						}	
					}
				}
			}
			if (evalStack.size() == 1) {
				return evalStack.peek() == 1 ? true : false;
			}
			else if (evalStack.size() == 0) {
				return true;
			}
			else {
				throw new Exception();
			}
		} catch (Exception e) {
			System.out.println("Error : Where clause arguments are incorrect (AND/OR conditions wrong)!");
			System.exit(0);
		}
		return false;
	}
	
	boolean operate (int a, int b, int operator) {
		if (operator == 0 || operator == 1)
			return a != b;
		else if (operator == 2)
			return a <= b;
		else if (operator == 3)
			return a < b;
		else if (operator == 4)
			return a >= b;
		else if (operator == 5)
			return a > b;
		else if (operator == 6)
			return a == b;
		else 
			return false;
	}
	
	CustomEntry<String, String> findField(String fieldName) {
		String tableName = null;
		int curPos = -1;
		if((curPos = fieldName.indexOf(".")) != -1) {
			tableName = fieldName.substring(0, curPos);
			fieldName = fieldName.substring(curPos + 1);
		}
		if (tableName != null) {
			if (schema.get(tableName).contains(fieldName)) {
				return new CustomEntry<String, String>(tableName, fieldName);
			}
			else {
				System.out.println("Error! Invalid Table or Column specified!");
				System.exit(0);
			}
		}
		else {
			String ans = countOccurencesFields(fieldName);
			if (ans.equals("ambiguous")) {
				System.out.println("Error : Ambiguous fields specified!");
				System.exit(0);
			}
			else if (ans.equals("number")) {
				return new CustomEntry<String, String>("pure_integer", fieldName);
			}
			else {
				return new CustomEntry<String, String>(ans, fieldName);
			}
		}
		return null;
	}
		
	String countOccurencesFields(String fieldName) {
		String tableName = null;
		int count = 0;
		Iterator<Entry<String, Set<String>>> it = schema.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Set<String>> pair = (Map.Entry<String, Set<String>>)it.next();
			for (String s : pair.getValue()) {
				if (s.equals(fieldName)) {
					tableName = pair.getKey();
					count++;
				}
			}
		}
		if (count == 0) {
			try {
				Integer.parseInt(fieldName);
			} catch (NumberFormatException e) {
				System.out.println("Error : Invalid argument given to one of the fields in the where clause!");
				System.exit(0);
			}
		}
		else if (count == 1) {
			return tableName;
		}
		else {
			return "ambiguous";
		}
		return "number";
	}
	
	Integer findInRecord(Map.Entry<String, String> field, HashMap<Map.Entry<String, String>, Integer> record) {
		Iterator<Entry<Entry<String, String>, Integer>> it = record.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Map.Entry<String, String>, Integer> pair = (Map.Entry<Map.Entry<String,String>, Integer>)it.next();
			if (pair.getKey().getKey().equals(field.getKey()) && pair.getKey().getValue().equals(field.getValue())) {
				return pair.getValue();
			}
		}
		return null;
	}
	
	/* Utility functions */
	
	void checkTableExists(String tableName) {
		File f = new File(tableName + ".csv");
		if (!f.exists()) {
			System.out.println("Error : One or more tables mentioned do not exist! Please check SELECT, FROM and WHERE clauses!");
			System.out.println("File containing table may be placed at an incorrect location or doesn't exist at all!");
			System.exit(0);
		}
	}
	
	String strip(String s) {
		if ( s != null && !s.isEmpty()) {
			return s.replaceAll("\\s+", "");
		}
		return null;
	}
	
	void display() {
		if (isStar == 1) {
			Iterator<Entry<Entry<String, String>, Integer>> it = result.get(0).entrySet().iterator();
			int c = 0;
			while (it.hasNext()) {
				Map.Entry<Map.Entry<String, String>, Integer> temp = (Map.Entry<Map.Entry<String,String>, Integer>)it.next();
				if (c == 0)
					System.out.print(temp.getKey().getKey() + "." + temp.getKey().getValue());
				else
					System.out.print(", " + temp.getKey().getKey() + "." + temp.getKey().getValue());
				c++;
			}
			System.out.println("");
			for (int i = 0; i < result.size(); i++) {
				it = result.get(i).entrySet().iterator();
				int count = 0;
				while (it.hasNext()) {
					Map.Entry<Map.Entry<String, String>, Integer> temp = (Map.Entry<Map.Entry<String,String>, Integer>)it.next();
					if (count == 0)
						System.out.print(temp.getValue());
					else
						System.out.print(", " + temp.getValue());
					count++;
				}
				System.out.println("");
			}
		}
		else if (aggregators.size() != 0) {
			for (int l = 0; l < aggregators.size(); l++) {
				String current[] = aggregators.get(l).split(":");
				int count = 0;
				Iterator<Entry<String, Set<String>>> it = schema.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry<String, Set<String>> pair = (Map.Entry<String, Set<String>>)it.next();
					for (String s : pair.getValue()) {
						if (s.equals(current[2])) {
							if (current[1].equals(" ")) {
								current[1] = pair.getKey();
								count++;
							}
							else if (current[1].equals(pair.getKey())){
								count = 1;
							}
							break;
						}
					}
				}
				if (count > 1) {
					System.out.println("Error : Ambiguous fields specified!");
					System.exit(0);
				}
				else if (count == 0) {
					System.out.println("Error: Field does not exist!");
					System.exit(0);
				}
				if (l == 0)
					System.out.print(current[0] + "(" + current[1] + "." + current[2] + ")");
				else
					System.out.print(", " + current[0] + "(" + current[1] + "." + current[2] + ")");
				aggregators.set(l, current[0] + ":" +current[1] + ":" + current[2]);
			}
			System.out.println("");
			for (int l = 0; l < aggregators.size(); l++) {
				String current[] = aggregators.get(l).split(":");
				int max = Integer.MIN_VALUE, min = Integer.MAX_VALUE, sum = 0, avg = 0, count = 0;
				for (int i = 0; i < result.size(); i++) {
					Iterator<Entry<Entry<String, String>, Integer>> it = result.get(i).entrySet().iterator();
					while (it.hasNext()) {
						Map.Entry<Map.Entry<String, String>, Integer> temp = (Map.Entry<Map.Entry<String,String>, Integer>)it.next();
						if (temp.getKey().getKey().equals(current[1]) && temp.getKey().getValue().equals(current[2])) {
							if (current[0].equals("max")) {
								if (temp.getValue() > max) {
									max = temp.getValue();
								}
							}
							else if (current[0].equals("min")) {
								if (temp.getValue() < min) {
									min = temp.getValue();
								}
							}
							else if (current[0].equals("sum")) {
								sum += temp.getValue();
							}
							else if (current[0].equals("avg")) {
								sum += temp.getValue();
								count++;
							}
							break;
						}
					}
				}
				if (l == 0) {
					if (current[0].equals("max")) {
						System.out.print(max);
					}
					else if (current[0].equals("min")) {
						System.out.print(min);
					}
					else if (current[0].equals("sum")) {
						System.out.print(sum);
					}
					else if (current[0].equals("avg")) {
						System.out.print(sum/(double)count);
					}
				}
				else {
					if (current[0].equals("max")) {
						System.out.print(", " + max);
					}
					else if (current[0].equals("min")) {
						System.out.print(", " + min);
					}
					else if (current[0].equals("sum")) {
						System.out.print(", " + sum);
					}
					else if (current[0].equals("avg")) {
						System.out.print(", " + sum/(double)count);
					}
				}
			}
			System.out.println("");
		}
		else if(isDistinct == 1) {
			for (int i = 0; i < selectFields.size(); i++) {
				String fieldName = selectFields.get(i).getKey();
				String tableName = selectFields.get(i).getValue();
				int count = 0;
				Iterator<Entry<String, Set<String>>> it = schema.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry<String, Set<String>> pair = (Map.Entry<String, Set<String>>)it.next();
					boolean flag = false;
					for (String s : pair.getValue()) {
						if (s.equals(fieldName)) {
							if (tableName == null) {
								tableName = pair.getKey();
								count++;
							}
							else if (tableName.equals(pair.getKey())) {
								count = 1;
							}
							break;
						}
					}
				}
				if (count > 1) {
					System.out.println("Error : Ambiguous fields specified!");
					System.exit(0);
				}
				else if (count == 0) {
					System.out.println("Error: Field does not exist!");
					System.exit(0);
				}
				selectFields.get(i).setValue(tableName);
				if (i == 0)
					System.out.print(tableName + "." + fieldName);
				else
					System.out.print(", " + tableName + "." + fieldName);
			}
			System.out.println("");
			ArrayList<String> sorted = new ArrayList<String>();
			for (int i = 0; i < result.size(); i++) {
				String r = "";
				for (int j = 0; j < selectFields.size(); j++) {
					String fieldName = selectFields.get(j).getKey();
					String tableName = selectFields.get(j).getValue();
					Iterator<Entry<Entry<String, String>, Integer>> it = result.get(i).entrySet().iterator();
					while (it.hasNext()) {
						Map.Entry<Map.Entry<String, String>, Integer> temp = (Map.Entry<Map.Entry<String,String>, Integer>)it.next();
						if (temp.getKey().getKey().equals(tableName) && temp.getKey().getValue().equals(fieldName)) {
							if (j == 0)
								r += temp.getValue();
							else 
								r += ", " + temp.getValue();
							break;
						}
					}
				}
				sorted.add(r);
			}
			int visited[] = new int[sorted.size()];
			Arrays.fill(visited, 0);
			for (int i = 0; i < sorted.size(); i++) {
				if (visited[i] == 0) {
					visited[i] = 1;
					System.out.println(sorted.get(i));
					for (int j = 0; j < sorted.size(); j++) {
						if (i != j) {
							if (sorted.get(j).equals(sorted.get(i))) {
								visited[j] = 1;
							}
						}
					}
				}
			}
		}
		else {
			for (int i = 0; i < selectFields.size(); i++) {
				String fieldName = selectFields.get(i).getKey();
				String tableName = selectFields.get(i).getValue();
				int count = 0;
				Iterator<Entry<String, Set<String>>> it = schema.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry<String, Set<String>> pair = (Map.Entry<String, Set<String>>)it.next();
					boolean flag = false;
					for (String s : pair.getValue()) {
						if (s.equals(fieldName)) {
							if (tableName == null) {
								tableName = pair.getKey();
								count++;
							}
							else if (tableName.equals(pair.getKey())) {
								count = 1;
							}
							break;
						}
					}
				}
				if (count > 1) {
					System.out.println("Error : Ambiguous fields specified!");
					System.exit(0);
				}
				else if (count == 0) {
					System.out.println("Error: Field does not exist!");
					System.exit(0);
				}
				selectFields.get(i).setValue(tableName);
				if (i == 0)
					System.out.print(tableName + "." + fieldName);
				else
					System.out.print(", " + tableName + "." + fieldName);
			}
			System.out.println("");
			for (int i = 0; i < result.size(); i++) {
				for (int j = 0; j < selectFields.size(); j++) {
					String fieldName = selectFields.get(j).getKey();
					String tableName = selectFields.get(j).getValue();
					Iterator<Entry<Entry<String, String>, Integer>> it = result.get(i).entrySet().iterator();
					while (it.hasNext()) {
						Map.Entry<Map.Entry<String, String>, Integer> temp = (Map.Entry<Map.Entry<String,String>, Integer>)it.next();
						if (temp.getKey().getKey().equals(tableName) && temp.getKey().getValue().equals(fieldName)) {
							if (j == 0)
								System.out.print(temp.getValue());
							else 
								System.out.print(", " + temp.getValue());
							break;
						}
					}
				}
				System.out.println("");
			}
		}
	}
}


