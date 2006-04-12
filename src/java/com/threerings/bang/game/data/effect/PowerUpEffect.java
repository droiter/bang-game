//
// $Id$

package com.threerings.bang.game.data.effect;

import com.threerings.bang.game.data.piece.Influence;
import com.threerings.bang.game.data.piece.Piece;
import com.threerings.bang.game.data.piece.Unit;

/**
 * An effect that gives a 30% attack bonus across the board to the piece in
 * question.
 */
public class PowerUpEffect extends SetInfluenceEffect
{
    @Override // documentation inherited
    protected Influence createInfluence (Unit target)
    {
        return new Influence() {
            public String getIcon () {
                return "power_up";
            }
            public int adjustAttack (Piece target, int damage) {
                return (int)Math.round(1.3f * damage);
            }
        };
    }

    @Override // documentation inherited
    protected String getEffectName ()
    {
        return "bonuses/power_up/activate";
    }
}
