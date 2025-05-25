package org.example;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseTransaction implements AutoCloseable {
    private final Connection connection;
    private boolean committed = false;

    public DatabaseTransaction(Connection connection) throws SQLException {
        this.connection = connection;
        connection.setAutoCommit(false);
    }

    public Connection getConnection() {
        return connection;
    }

    public void commit() throws SQLException {
        connection.commit();
        committed = true;
    }

    public void rollback() throws SQLException {
        if (!committed) {
            connection.rollback();
        }
    }

    @Override
    public void close() throws SQLException {
        try {
            if (!committed) {
                rollback();
            }
        } finally {
            connection.setAutoCommit(true);
        }
    }
}
