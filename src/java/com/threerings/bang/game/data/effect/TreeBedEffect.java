//
// $Id$

package com.threerings.bang.game.data.effect;

import com.samskivert.util.IntIntMap;

import com.threerings.bang.data.Stat;

import com.threerings.bang.game.data.BangObject;
import com.threerings.bang.game.data.piece.LoggingRobot;
import com.threerings.bang.game.data.piece.Piece;
import com.threerings.bang.game.data.piece.TreeBed;
import com.threerings.bang.game.data.scenario.ForestGuardiansInfo;
import com.threerings.bang.game.util.PieceUtil;

import static com.threerings.bang.Log.log;

/**
 * Communicates that a tree grew.
 */
public class TreeBedEffect extends Effect
{
    /** Indicates that the tree sprouted from a stump. */
    public static final String SPROUTED = "indian_post/tree_bed/sprouted";
    
    /** Indicates that the tree grew towards the next stage. */
    public static final String GREW = "indian_post/tree_bed/grew";

    /** Indicates that the tree grew into another stage. */
    public static final String BLOOMED = "indian_post/tree_bed/bloomed";
    
    /** The id of the affected tree bed. */
    public int bedId;

    /** The ids of the pieces growing or shrinking the tree. */
    public int[] pieceIds;

    /** The amount of damage inflicted. */
    public int damage;

    /** If greater than zero, the growth value to which the tree
     * should be reset. */
    public byte growth = -1;

    /**
     * No-arg constructor for deserialization.
     */
    public TreeBedEffect ()
    {
    }

    /**
     * Creates a new tree bed effect that will grow or damage the tree.
     *
     * @param bed the affected bed
     * @param pieces the pieces growing or shrinking the tree
     * @param damage the amount of damage inflicted
     */
    public TreeBedEffect (TreeBed bed, Piece[] pieces, int damage)
    {
        bedId = bed.pieceId;
        pieceIds = new int[pieces.length];
        for (int ii = 0; ii < pieces.length; ii++) {
            pieceIds[ii] = pieces[ii].pieceId;
        }
        this.damage = damage;
    }

    /**
     * Creates a tree bed effect that will reset the tree with the
     * given amount of initial damage and growth.
     */
    public TreeBedEffect (TreeBed bed, int damage, int growth)
    {
        bedId = bed.pieceId;
        pieceIds = NO_PIECES;
        this.damage = damage;
        this.growth = (byte)growth;
    }

    // documentation inherited
    public int[] getAffectedPieces ()
    {
        return new int[] { bedId };
    }

    @Override // documentation inherited
    public int[] getWaitPieces ()
    {
        return pieceIds;
    }

    // documentation inherited
    public void prepare (BangObject bangobj, IntIntMap dammap)
    {
        // award points to the growers if the growth is positive
        if (damage > 0) {
            return;
        }
        for (int pieceId : pieceIds) {
            Piece piece = bangobj.pieces.get(pieceId);
            if (piece == null || piece.owner == -1) {
                continue;
            }
            bangobj.stats[piece.owner].incrementStat(Stat.Type.TREE_POINTS,
                ForestGuardiansInfo.POINTS_PER_TREE_GROWTH);
            bangobj.grantPoints(piece.owner,
                ForestGuardiansInfo.POINTS_PER_TREE_GROWTH);
        }
    }

    // documentation inherited
    public boolean apply (BangObject bangobj, Observer observer)
    {
        // find the tree
        TreeBed bed = (TreeBed)bangobj.pieces.get(bedId);
        if (bed == null) {
            log.warning("Missing piece for tree bed effect [pieceId=" +
                bedId + "].");
            return false;
        }
        
        // enact the tree's growth or damage
        if (growth >= 0) {
            bed.growth = growth;
            bed.damage = damage;
            reportEffect(observer, bed, SPROUTED);
        } else {
            int ogrowth = bed.growth;
            bed.damage(damage);
            reportEffect(observer, bed, GREW);
            if (bed.growth > ogrowth) {
                reportEffect(observer, bed, BLOOMED);
            }
        }
        return true;
    }

    @Override // documentation inherited
    public int getBaseDamage (Piece piece)
    {
        return damage;
    }
}
