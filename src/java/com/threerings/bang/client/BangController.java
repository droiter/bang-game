//
// $Id$

package com.threerings.bang.client;

import com.threerings.crowd.client.PlaceView;
import com.threerings.crowd.data.BodyObject;
import com.threerings.crowd.data.PlaceConfig;
import com.threerings.crowd.data.PlaceObject;
import com.threerings.crowd.util.CrowdContext;

import com.threerings.parlor.game.client.GameController;

import com.threerings.bang.data.BangConfig;
import com.threerings.bang.data.BangObject;
import com.threerings.bang.data.card.Card;
import com.threerings.bang.util.BangContext;

import static com.threerings.bang.Log.log;

/**
 * Handles the logic and flow of the client side of a game.
 */
public class BangController extends GameController
{
    /** The name of the command posted by the "Back to lobby" button in
     * the side bar. */
    public static final String BACK_TO_LOBBY = "BackToLobby";

    /** A command that requests to place a card. */
    public static final String PLACE_CARD = "PlaceCard";

    @Override // documentation inherited
    public void init (CrowdContext ctx, PlaceConfig config)
    {
        super.init(ctx, config);
        _ctx = (BangContext)ctx;
        _config = (BangConfig)config;
    }

    @Override // documentation inherited
    public void willEnterPlace (PlaceObject plobj)
    {
        super.willEnterPlace(plobj);
        _bangobj = (BangObject)plobj;

        // determine our player index
        BodyObject me = (BodyObject)_ctx.getClient().getClientObject();
        _pidx = _bangobj.getPlayerIndex(me.username);

        // we may be returning to an already started game
        if (_bangobj.state != BangObject.AWAITING_PLAYERS) {
            handleStateChange(_bangobj.state);
        }
    }

    /** Handles a request to leave the game. Generated by the {@link
     * #BACK_TO_LOBBY} command. */
    public void handleBackToLobby (Object source)
    {
        _ctx.getLocationDirector().moveBack();
    }

    /** Handles a request to move a piece. */
    public void moveAndFire (int pieceId, int tx, int ty, final int targetId)
    {
        BangService.InvocationListener il =
            new BangService.InvocationListener() {
            public void requestFailed (String reason) {
                // TODO: play a sound or highlight the piece that failed
                // to move
                log.info("Thwarted! " + reason);

                // clear any pending shot indicator
                if (targetId != -1) {
                    _view.view.shotFailed(targetId);
                }
            }
        };
        log.info("Requesting move and fire [pid=" + pieceId +
                 ", to=+" + tx + "+" + ty + ", tid=" + targetId + "].");
        _bangobj.service.move(
            _ctx.getClient(), pieceId, (short)tx, (short)ty, targetId, il);
    }

    /** Handles a request to place a card. */
    public void placeCard (int cardId)
    {
        if (_bangobj == null || !_bangobj.isInPlay()) {
            return;
        }

        Card card = (Card)_bangobj.cards.get(cardId);
        if (card == null) {
            log.warning("Requested to place non-existent card '" +
                        cardId + "'.");
        } else {
            // instruct the board view to activate placement mode
            _view.view.placeCard(card);
        }
    }

    /** Handles a request to activate a card. */
    public void activateCard (int cardId, int tx, int ty)
    {
        if (_bangobj.cards.get(cardId) == null) {
            log.warning("Requested to activate expired card " +
                        "[id=" + cardId + "].");
        } else {
            _bangobj.service.playCard(
                _ctx.getClient(), cardId, (short)tx, (short)ty);
        }
    }

    @Override // documentation inherited
    protected PlaceView createPlaceView (CrowdContext ctx)
    {
        _view = new BangView((BangContext)ctx, this);
        return _view;
    }

    @Override // documentation inherited
    protected boolean handleStateChange (int state)
    {
        if (state == BangObject.SELECT_PHASE) {
            _view.selectionPhase(_bangobj, _config, _pidx);
            return true;

        } else if (state == BangObject.BUYING_PHASE) {
            _view.buyingPhase(_bangobj, _config, _pidx);
            return true;

        } else if (state == BangObject.POST_ROUND) {
            _view.view.endRound();
            return true;

        } else {
            return super.handleStateChange(state);
        }
    }

    @Override // documentation inherited
    protected void gameDidStart ()
    {
        super.gameDidStart();

        // we may be returning to an already started game
        _view.startGame(_bangobj, _config, _pidx);
    }

    @Override // documentation inherited
    protected void gameWillReset ()
    {
        super.gameWillReset();
        _view.endGame();
    }

    @Override // documentation inherited
    protected void gameDidEnd ()
    {
        super.gameDidEnd();
        _view.endGame();
    }

    /** A casted reference to our context. */
    protected BangContext _ctx;

    /** The configuration of this game. */
    protected BangConfig _config;

    /** Contains our main user interface. */
    protected BangView _view;

    /** A casted reference to our game object. */
    protected BangObject _bangobj;

    /** Our player index or -1 if we're not a player. */
    protected int _pidx;
}
