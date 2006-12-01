//
// $Id$

package com.threerings.bang.server.persist;

import java.sql.Timestamp;
import java.sql.Timestamp;

import com.samskivert.util.StringUtil;

import com.threerings.bang.data.BangCodes;
import com.threerings.bang.data.Handle;

/**
 * A record containing persistent information maintained about a Bang!
 * player.
 */
public class PlayerRecord
{
    /** A flag indicating the player's gender. */
    public static final int IS_MALE_FLAG = 1 << 0;

    /** This player's unique identifier. */
    public int playerId;

    /** The authentication account name associated with this player. */
    public String accountName;

    /** The cowboy handle (in-game name) associated with this player. */
    public String handle;

    /** The amount of scrip this player holds. */
    public int scrip;

    /** The current avatar look selected by this player. */
    public String look;

    /** The avatar look selected by this player for their victory pose. */
    public String victoryLook;

    /** The avatar look selected by this player for their wanted poster. */
    public String wantedLook;

    /** The id of the player's gang, if any. */
    public int gangId;

    /** The player's rank in their gang. */
    public byte gangRank;

    /** The time at which the player joined or created their gang. */
    public Timestamp joinedGang;

    /** The id of the furthest town to which this player has access. */
    public String townId;

    /** The time at which this player was created (when they first starting
     * playing  this particular game). */
    public Timestamp created;

    /** The number of sessions this player has played. */
    public int sessions;

    /** The cumulative number of minutes spent playing. */
    public int sessionMinutes;

    /** The time at which the player ended their last session. */
    public Timestamp lastSession;

    /** Various one bit data (gender, etc.). */
    public int flags;

    /** A blank constructor used when loading records from the database. */
    public PlayerRecord ()
    {
    }

    /** Constructs a blank player record for the supplied account. */
    public PlayerRecord (String accountName)
    {
        this.accountName = accountName;
        this.look = "";
        this.victoryLook = "";
        this.wantedLook = "";
        this.townId = BangCodes.FRONTIER_TOWN;
    }

    /** Returns true if the specified flag is set. */
    public boolean isSet (int flag)
    {
        return (flags & flag) == flag;
    }

    /** Returns our handle as a proper {@link Handle} instance. */
    public Handle getHandle ()
    {
        return new Handle(handle);
    }

    /** Generates a string representation of this instance. */
    public String toString ()
    {
        return StringUtil.fieldsToString(this);
    }
}
