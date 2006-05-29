//
// $Id$

package com.threerings.bang.game.data.effect;

import com.samskivert.util.IntIntMap;

import com.threerings.bang.game.client.EffectHandler;
import com.threerings.bang.game.client.AreaDamageHandler;
import com.threerings.bang.game.data.BangObject;
import com.threerings.bang.game.data.piece.Piece;

/**
 * An effect that does damage to all units within a certain area.
 */
public class AreaDamageEffect extends AreaEffect
{
    /** The identifier for the type of effect that we produce. */
    public static final String MISSILED = "howdy";

    /** The index of the player causing the damage or -1. */
    public int causer;

    /** The base damage for this effect. */
    public int baseDamage;

    /** The updated damage for the affected pieces. */
    public int[] newDamage;

    /** Death effects corresponding to each piece (<code>null</code> for pieces
     * that didn't die or didn't produce a death effect). */
    public Effect[] deathEffects;
    
    public AreaDamageEffect ()
    {
    }

    public AreaDamageEffect (int causer, int damage, int radius, int x, int y)
    {
        super(radius, x, y);
        this.causer = causer;
        this.baseDamage = damage;
    }

    @Override // documentation inherited
    public void prepare (BangObject bangobj, IntIntMap dammap)
    {
        super.prepare(bangobj, dammap);

        // determine the damage for each piece
        newDamage = new int[pieces.length];
        deathEffects = new Effect[pieces.length];
        for (int ii = 0; ii < pieces.length; ii++) {
            Piece target = (Piece)bangobj.pieces.get(pieces[ii]);
            int damage = getDamage(target);
            newDamage[ii] = target.damage + damage;
            dammap.increment(target.owner, damage);
            if (newDamage[ii] == 100) {
                deathEffects[ii] = target.willDie(bangobj, -1);
                deathEffects[ii].prepare(bangobj, dammap);
            }
        }
    }
    
    @Override // documentation inherited
    public EffectHandler createHandler (BangObject bangobj)
    {
        return new AreaDamageHandler();
    }

    /**
     * Returns the damage done to the specified piece.
     */
    protected int getDamage (Piece piece)
    {
        int damage = baseDamage / (piece.getDistance(x, y) + 1);
        return Math.min(damage, 100-piece.damage);
    }
    
    @Override // documentation inherited
    protected void apply (
        BangObject bangobj, Observer obs, int pidx, Piece piece, int dist)
    {
        if (deathEffects[pidx] != null) {
            deathEffects[pidx].apply(bangobj, obs);
        }
        damage(bangobj, obs, causer, piece, newDamage[pidx], MISSILED);
    }
}
