//
// $Id$

package com.threerings.bang.game.data.piece;

import com.threerings.bang.game.client.sprite.HomesteadSprite;
import com.threerings.bang.game.client.sprite.PieceSprite;

/**
 * Represents a Homestead piece in the Land Grab scenario.
 */
public class Homestead extends Prop
{
    /** Tracks our previous owner for scoring purposes. */
    public transient int previousOwner = -1;

    @Override // documentation inherited
    public PieceSprite createSprite ()
    {
        return new HomesteadSprite();
    }

    @Override // documentation inherited
    public boolean isTargetable ()
    {
        return (owner != -1);
    }

    @Override // documentation inherited
    public boolean willBeTargetable ()
    {
        return true;
    }

    @Override // documentation inherited
    public int getTicksPerMove ()
    {
        return Integer.MAX_VALUE;
    }

    @Override // documentation inherited
    public float getHeight ()
    {
        // sprite is positioned according to board height, so make sure
        // the piece itself doesn't contribute
        return 0f;
    }
    
    @Override // documentation inherited
    public boolean isOwnerConfigurable ()
    {
        return true;
    }
    
    @Override // documentation inherited
    public void wasKilled (short tick)
    {
        // clear out our ownership
        previousOwner = owner;
        owner = -1;

        // and reset our damage
        damage = 0;
    }
}
