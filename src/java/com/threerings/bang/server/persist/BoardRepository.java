//
// $Id$

package com.threerings.bang.server.persist;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;

import com.samskivert.io.PersistenceException;
import com.samskivert.jdbc.ConnectionProvider;
import com.samskivert.jdbc.DatabaseLiaison;
import com.samskivert.jdbc.JDBCUtil;
import com.samskivert.jdbc.JORARepository;
import com.samskivert.jdbc.jora.Cursor;
import com.samskivert.jdbc.jora.FieldMask;
import com.samskivert.jdbc.jora.Session;
import com.samskivert.jdbc.jora.Table;

import static com.threerings.bang.Log.*;

/**
 * Handles the loading and management of our persistent board data.
 */
public class BoardRepository extends JORARepository
{
    /** Type definition! */
    public static class BoardList extends ArrayList<BoardRecord>
    {
    }

    /** The database identifier used when establishing a database
     * connection. This value being <code>boarddb</code>. */
    public static final String BOARD_DB_IDENT = "boarddb";

    /**
     * Constructs a new board repository with the specified connection
     * provider.
     *
     * @param conprov the connection provider via which we will obtain our
     * database connection.
     */
    public BoardRepository (ConnectionProvider conprov)
        throws PersistenceException
    {
        super(conprov, BOARD_DB_IDENT);
        
        // TEMP: add the DATA_HASH column to the table
        execute(new Operation() {
            public Object invoke (Connection conn, DatabaseLiaison liaison)
                throws SQLException, PersistenceException
            {
                if (!JDBCUtil.tableContainsColumn(conn, "BOARDS",
                    "DATA_HASH")) {
                    JDBCUtil.addColumn(conn, "BOARDS", "DATA_HASH",
                        "BLOB NOT NULL", "DATA");
                }
                return null;
            }
        });
    }

    /**
     * Loads all boards in the whole database without their board data.
     */
    public BoardList loadBoards ()
        throws PersistenceException
    {
        final BoardList blist = new BoardList();
        execute(new Operation() {
            public Object invoke (Connection conn, DatabaseLiaison liaison)
                throws SQLException, PersistenceException
            {
                Statement stmt = conn.createStatement();
                try {
                    ResultSet rs = stmt.executeQuery(
                        "select BOARD_ID, NAME, CREATOR, SCENARIOS, " +
                        "PLAYERS, PLAYS from BOARDS");
                    while (rs.next()) {
                        BoardRecord brec = new BoardRecord();
                        brec.boardId = rs.getInt(1);
                        brec.name = rs.getString(2);
                        brec.creator = rs.getString(3);
                        brec.scenarios = rs.getString(4);
                        brec.players = rs.getInt(5);
                        brec.plays = rs.getInt(6);
                        blist.add(brec);
                    }
                    
                } finally {
                    JDBCUtil.close(stmt);
                }
                return null;
            }
        });
        return blist;
    }

    /**
     * Loads the board data and data hash for the given board.
     */
    public void loadBoardData (final BoardRecord brec)
        throws PersistenceException
    {
        execute(new Operation() {
            public Object invoke (Connection conn, DatabaseLiaison liaison)
                throws SQLException, PersistenceException
            {
                PreparedStatement stmt = conn.prepareStatement(
                    "select DATA, DATA_HASH from BOARDS where BOARD_ID = ?");
                try {
                    stmt.setInt(1, brec.boardId);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        brec.data = (byte[])rs.getObject(1);
                        brec.dataHash = (byte[])rs.getObject(2);
                        
                    } else {
                        log.warning("Couldn't find board data! [brec=" + brec +
                            "].");
                    }
                    
                } finally {
                    JDBCUtil.close(stmt);
                }
                return null;
            }
        });
    }
    
    /**
     * Inserts a new board into the repository for the first time. If the
     * board's name conflicts with an existing board an exception is
     * thrown.
     */
    public void createBoard (BoardRecord record)
        throws PersistenceException
    {
        record.boardId = insert(_btable, record);
    }

    /**
     * Updates the supplied board if another board exists with the same
     * name, otherwise inserts the record as a new board.
     */
    public void storeBoard (BoardRecord record)
        throws PersistenceException
    {
        FieldMask mask = _btable.getFieldMask();
        mask.setModified("name");
        mask.setModified("players");
        BoardRecord orecord = (BoardRecord)loadByExample(_btable, mask, record);
        if (orecord != null) {
            record.boardId = orecord.boardId;
            update(_btable, record);
        } else {
            record.boardId = insert(_btable, record);
        }
    }

    @Override // documentation inherited
    protected void createTables (Session session)
    {
	_btable = new Table(BoardRecord.class.getName(), "BOARDS",
                            session, "BOARD_ID", true);
    }

    protected Table _btable;
}
