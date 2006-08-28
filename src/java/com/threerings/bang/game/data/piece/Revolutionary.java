//
// $Id$

package com.threerings.bang.game.data.piece;

import com.samskivert.util.ArrayUtil;

import com.threerings.bang.data.UnitConfig;

import com.threerings.bang.game.client.sprite.PieceSprite;

import com.threerings.bang.game.data.BangObject;
import com.threerings.bang.game.data.effect.ShotEffect;

/**
 * Handles the special capabilities of the Revolutionary unit..
 */
public class Revolutionary extends Unit
{
    @Override // documentation inherited
    public int adjustPieceAttack (Piece attacker, int damage)
    {
        damage = super.adjustPieceAttack(attacker, damage);
        // give other allied ground units a %10 attack bonus
        if (attacker.owner == owner && attacker.pieceId != pieceId &&
                attacker instanceof Unit && ((Unit)attacker).getConfig().mode 
                == UnitConfig.Mode.GROUND) {
            damage = (int)(damage * 1.1f);
        }
        return damage;
    }

    @Override // documentation inherited
    protected ShotEffect unitShoot (
            BangObject bangobj, Piece target, float scale)
    {
        // if he can use his sword he gets an attack bonus
        boolean proximity = false;
        if (getDistance(target) == 1 && !target.isAirborne() &&
                bangobj.board.canCross(x, y, target.x, target.y)) {
            scale *= SWORD_ATTACK_BONUS;
            proximity = true;
        }
        ShotEffect shot = super.unitShoot(bangobj, target, scale);
        if (shot != null && proximity) {
            if (shot.attackIcons == null) {
                shot.attackIcons = new String[] { "revolutionary" };
            } else {
                shot.attackIcons = (String[])ArrayUtil.append(
                        shot.attackIcons, "revolutionary");
            }
            shot.type = ShotEffect.PROXIMITY;
        }
        return shot;
    }

    protected static final float SWORD_ATTACK_BONUS = 1.25f;
}
