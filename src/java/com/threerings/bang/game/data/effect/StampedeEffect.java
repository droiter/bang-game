//
// $Id$

package com.threerings.bang.game.data.effect;

import java.awt.Point;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;

import com.samskivert.util.ArrayUtil;
import com.samskivert.util.IntIntMap;
import com.samskivert.util.ListUtil;

import com.threerings.io.SimpleStreamableObject;
import com.threerings.util.RandomUtil;

import com.threerings.bang.game.client.EffectHandler;
import com.threerings.bang.game.client.StampedeHandler;
import com.threerings.bang.game.data.BangBoard;
import com.threerings.bang.game.data.BangObject;
import com.threerings.bang.game.util.PointList;
import com.threerings.bang.game.data.piece.Piece;
import com.threerings.bang.game.data.piece.PieceCodes;
import com.threerings.bang.game.data.piece.Unit;

import static com.threerings.bang.Log.*;

/**
 * Represents the effect of a stampede running over the board.
 */
public class StampedeEffect extends Effect
    implements PieceCodes
{
    /** The speed of the buffalo in tiles per second. */
    public static final float BUFFALO_SPEED = 4f;

    /** The amount of damage taken by units hit by buffalo. */
    public static final int COLLISION_DAMAGE = 20;

    /** The identifier for the type of effect that we produce. */
    public static final String DAMAGED = "bang";

    /**
     * Represents a buffalo's collision with a unit.
     */
    public static class Collision extends SimpleStreamableObject
    {
        /** The timestep at which the collision occurred. */
        public int step;

        /** The id of the unit hit. */
        public int targetId;

        /** The coordinates to which the unit was pushed. */
        public short x, y;

        public Collision ()
        {
        }

        public Collision (int step, int targetId, int x, int y)
        {
            this.step = step;
            this.targetId = targetId;
            this.x = (short)x;
            this.y = (short)y;
        }
    }

    /** The id of the player causing the damage or -1. */
    public transient int causer;

    /** The location selected. */
    public transient int x, y;

    /** The radius of the effect. */
    public transient int radius;

    /** The path to be followed by the buffalo. */
    public PointList path;

    /** The list of collisions between buffalo and units. */
    public Collision[] collisions;

    public StampedeEffect ()
    {
    }

    public StampedeEffect (int causer, int x, int y, int radius)
    {
        this.causer = causer;
        this.x = x;
        this.y = y;
        this.radius = radius;
    }

    @Override // documentation inherited
    public int[] getAffectedPieces ()
    {
        int[] pieceIds = new int[collisions.length];
        for (int ii = 0; ii < pieceIds.length; ii++) {
            pieceIds[ii] = collisions[ii].targetId;
        }
        return pieceIds;
    }

    @Override // documentation inherited
    public void prepare (BangObject bangobj, IntIntMap dammap)
    {
        // create the path that the buffalo will follow
        createPath(bangobj.board);

        // create the list of collisions
        createCollisions(bangobj, dammap);
    }

    @Override // documentation inherited
    public void apply (BangObject bangobj, Observer obs)
    {
        // delay the tick by the amount of time it takes for the buffalo to run
        // their course
        reportDelay(obs, (long)((path.size()-1) * 1000 / BUFFALO_SPEED));

        // apply the collisions in order
        for (int ii = 0; ii < collisions.length; ii++) {
            Collision collision = collisions[ii];
            collide(bangobj, obs, causer, collision.targetId, COLLISION_DAMAGE,
                    collision.x, collision.y, DAMAGED);
        }
    }

    @Override // documentation inherited
    public EffectHandler createHandler (BangObject bangobj)
    {
        return new StampedeHandler();
    }

    /**
     * Creates a path for the buffalo.
     */
    protected void createPath (BangBoard board)
    {
        // starting at the target position, grow the path from both ends until
        // we're blocked or we've reached the maximum length
        path = new PointList();
        Point start = new Point(x, y);
        path.add(start);
        int hdir = growPath(board, path, -1);
        if (path.size() == 1) {
            log.warning("Couldn't find anywhere for the buffalo to go! " +
                "[effect=" + this + "]."); 
            return;
        }
        Collections.reverse(path);
        PointList rpath = new PointList();
        rpath.add(start);
        if (growPath(board, rpath, (hdir + 2) % 4) != -1) {
            path.addAll(rpath.subList(1, rpath.size()));
        }
    }
    
    /**
     * Extends the path by one tile.
     *
     * @param path the path so far
     * @param dir the preferred direction, or -1 for none
     * @return the direction taken, or -1 for none
     */
    protected int growPath (BangBoard board, PointList path, int dir)
    {
        Point last = path.get(path.size() - 1);
        int[] dirs;
        if (dir != -1) {
            int rot = (RandomUtil.getInt(2) == 0) ? 1 : 3;
            dirs = new int[] { dir, (dir + rot) % 4, (dir + rot + 2) % 4,
                (dir + 2) % 4 };
        } else {
            dirs = (int[])DIRECTIONS.clone();
            ArrayUtil.shuffle(dirs);
        }
        PointList bpath = null;
        int bdir = -1, marker = path.size();
        for (int ii = 0; ii < dirs.length; ii++) {
            Point next = new Point(last.x + DX[dirs[ii]],
                last.y + DY[dirs[ii]]);
            if (!board.isGroundOccupiable(next.x, next.y, true) ||
                path.contains(next)) {
                continue;
            }
            path.add(next);
            if (path.size() == STAMPEDE_LENGTH + 1) {
                return dirs[ii];
            }
            growPath(board, path, dirs[ii]);
            if (path.size() == STAMPEDE_LENGTH + 1) {
                return dirs[ii];
            }
            if (bpath == null || (path.size() - marker) > bpath.size()) {
                bpath = new PointList();
                bpath.addAll(path.subList(marker, path.size()));
                bdir = dirs[ii];
            }
            path.subList(marker, path.size()).clear();
        }
        if (bpath != null) {
            path.addAll(bpath);
        }
        return bdir;
    }
    
    /**
     * Creates the collision list for the buffalo.
     */
    protected void createCollisions (BangObject bangobj, IntIntMap dammap)
    {
        // clone all the non-flying units
        ArrayList<Piece> units = new ArrayList<Piece>();
        for (Iterator it = bangobj.pieces.iterator(); it.hasNext(); ) {
            Piece piece = (Piece)it.next();
            if (piece instanceof Unit && !piece.isFlyer()) {
                units.add((Piece)piece.clone());
            }
        }

        // step through the path, updating units and generating collisions
        ArrayList<Collision> cols = new ArrayList<Collision>();
        Point loc = new Point();
        for (int ii = 0, nn = path.size(); ii < nn; ii++) {
            for (Piece unit : units) {
                loc.setLocation(unit.x, unit.y);
                if (containsBuffalo(loc, ii)) {
                    // try to move the unit to a point that wasn't occupied by
                    // a buffalo in the last step and won't be in the next step
                    ArrayList<Point> nlocs = new ArrayList<Point>();
                    for (int jj = 0; jj < DIRECTIONS.length; jj++) {
                        Point nloc = new Point(loc.x + DX[jj], loc.y + DY[jj]);
                        if (bangobj.board.canOccupy(unit, nloc.x, nloc.y) &&
                            !containsBuffalo(nloc, ii - 1) &&
                            !containsBuffalo(nloc, ii + 1)) {
                            nlocs.add(nloc);
                        }
                    }
                    Point nloc = (nlocs.size() > 0 ?
                        (Point)RandomUtil.pickRandom(nlocs) : loc);
                    cols.add(new Collision(ii, unit.pieceId, nloc.x, nloc.y));
                    bangobj.board.clearShadow(unit);
                    unit.position(nloc.x, nloc.y);
                    bangobj.board.shadowPiece(unit);
                    dammap.increment(unit.owner, COLLISION_DAMAGE);
                }
            }
        }
        collisions = cols.toArray(new Collision[cols.size()]);
    }

    /**
     * Checks whether the specified location contains a buffalo at the given
     * step along the paths.
     */
    protected boolean containsBuffalo (Point loc, int step)
    {
        if (step < 0) {
            return false;
        }
        return path.size() > step && path.get(step).equals(loc);
    }

    /** The (maximum) length of the buffalo stampede in each direction. */
    protected static final int STAMPEDE_LENGTH = 4;
}
