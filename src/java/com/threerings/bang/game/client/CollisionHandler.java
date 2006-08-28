//
// $Id$

package com.threerings.bang.game.client;

import com.threerings.bang.game.client.sprite.MobileSprite;
import com.threerings.bang.game.client.sprite.PieceSprite;
import com.threerings.bang.game.data.effect.TrainEffect;
import com.threerings.bang.game.data.piece.Piece;

/**
 * Handles displaying the proper damage value with a damage icon during 
 * a collision.
 */
public class CollisionHandler extends EffectHandler
{
    public CollisionHandler (int damage)
    {
        _damage = damage;
    }

    @Override // documentation inherited
    public void pieceMoved (Piece piece)
    {
        MobileSprite ms = null;
        PieceSprite sprite = _view.getPieceSprite(piece);
        if (sprite != null && sprite instanceof MobileSprite) {
            ms = (MobileSprite)sprite;
            ms.setMoveAction(MobileSprite.MOVE_PUSH);
        }

        super.pieceMoved(piece);

        if (ms != null) {
            ms.setMoveAction(MobileSprite.MOVE_NORMAL);
        }
    }

    protected int _damage;
}
