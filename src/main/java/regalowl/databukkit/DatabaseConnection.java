package regalowl.databukkit;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.scheduler.BukkitTask;




public abstract class DatabaseConnection {

	protected DataBukkit dab;
	protected Connection connection;
	protected CopyOnWriteArrayList<String> statements = new CopyOnWriteArrayList<String>();
	protected BukkitTask writeTask;
	protected String currentStatement;
	protected PreparedStatement preparedStatement;
	protected boolean inUse;
	
	DatabaseConnection(DataBukkit dab) {
		this.dab = dab;
	}
	
	public void aSyncWrite(List<String> sql) {
		try {
			inUse = true;
			statements.clear();
			for (String csql : sql) {
				statements.add(csql);
			}
		} catch (Exception e) {
			dab.writeError(e, null);
		}
		writeTask = dab.getPlugin().getServer().getScheduler().runTaskAsynchronously(dab.getPlugin(), new Runnable() {
			public void run() {
				try {
					if (connection == null || connection.isClosed()) {
						openConnection();
					}
					connection.setAutoCommit(false);
					for (String statement:statements) {
						currentStatement = statement;
						preparedStatement = connection.prepareStatement(currentStatement);
						preparedStatement.executeUpdate();
					}
					connection.commit();
					statements.clear();
					inUse = false;
				} catch (SQLException e) {
					try {
						connection.rollback();
						dab.writeError(e, "SQL write failed.  The failing SQL statement is in the following brackets: [" + currentStatement + "]");
					} catch (SQLException e1) {
						dab.writeError(e, "Rollback failed.  Cannot recover.");
						return;
					}
					statements.remove(currentStatement);
					dab.getSQLWrite().executeSQL(statements);
					statements.clear();
					inUse = false;
				}
			}
		});
	}
	
	public void syncWrite(List<String> sql) {
		try {
			inUse = true;
			if (connection == null || connection.isClosed()) {
				openConnection();
			}
			connection.setAutoCommit(false);
			statements.clear();
			for (String csql:sql) {
				statements.add(csql);
			}
			for (String statement:statements) {
				currentStatement = statement;
				preparedStatement = connection.prepareStatement(currentStatement);
				preparedStatement.executeUpdate();
			}
			connection.commit();
			statements.clear();
			inUse = false;
		} catch (SQLException e) {
			try {
				connection.rollback();
				dab.writeError(e, "SQL write failed.  The failing SQL statement is in the following brackets: [" + currentStatement + "]");
			} catch (SQLException e1) {
				dab.writeError(e, "Rollback failed.  Cannot recover.");
				return;
			}
			statements.remove(currentStatement);
			dab.getSQLWrite().executeSQL(statements);
			statements.clear();
			inUse = false;
		}
	}
	
	
	/**
	 * This function should be run asynchronously to prevent slowing the main thread.
	 * @param statement
	 * @return QueryResult
	 */
	public QueryResult read(String statement) {
		QueryResult qr = new QueryResult();
		try {
			if (connection == null || connection.isClosed()) {
				openConnection();
			}
			Statement state = connection.createStatement();
			ResultSet resultSet = state.executeQuery(statement);
			ResultSetMetaData rsmd = resultSet.getMetaData();
			int columnCount = rsmd.getColumnCount();
			for (int i = 1; i <= columnCount; i++) {
				qr.addColumnName(rsmd.getColumnLabel(i));
			}
			while (resultSet.next()) {
				for (int i = 1; i <= columnCount; i++) {
					qr.addData(i, resultSet.getString(i));
				}
			}
			resultSet.close();
			state.close();
			statement = null;
			if (dab.getSQLRead() != null) {
				dab.getSQLRead().returnConnection(this);
			}
			return qr;
		} catch (SQLException e) {
			dab.writeError(e, "The failed SQL statement is in the following brackets: [" + statement + "]");
			if (dab.getSQLRead() != null) {
				dab.getSQLRead().returnConnection(this);
			}
			return qr;
		}
	}
	
	protected abstract void openConnection();
	
	public List<String> closeConnection() {
		if (writeTask != null) {
			writeTask.cancel();
			if (inUse) {
				try {
					connection.rollback();
				} catch (SQLException e) {
					dab.writeError(e, null);
				}
			}
		}
		try {
			connection.close();
		} catch (SQLException e) {}
		if (!inUse) {
			statements.clear();
		}
		return statements;
	}
	
	public boolean inUse() {
		return inUse;
	}
	
}
