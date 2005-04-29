//
// $Id$

package com.threerings.bang.client;

import java.awt.event.ActionEvent;

import com.samskivert.swing.event.CommandEvent;
import com.samskivert.util.StringUtil;

import com.threerings.presents.dobj.MessageEvent;
import com.threerings.presents.dobj.MessageListener;

import com.threerings.crowd.client.PlaceView;
import com.threerings.crowd.data.BodyObject;
import com.threerings.crowd.data.PlaceConfig;
import com.threerings.crowd.data.PlaceObject;
import com.threerings.crowd.util.CrowdContext;

import com.threerings.parlor.game.client.GameController;

import com.threerings.bang.data.BangConfig;
import com.threerings.bang.data.BangObject;
import com.threerings.bang.data.effect.Effect;
import com.threerings.bang.data.surprise.Surprise;
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

    /** A command that requests to move a piece. */
    public static final String MOVE_AND_FIRE = "MoveAndFire";

    /** A command that requests to place a surprise. */
    public static final String PLACE_SURPRISE = "PlaceSurprise";

    /** A command that requests to activate a surprise. */
    public static final String ACTIVATE_SURPRISE = "ActivateSurprise";

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

    /** Handles a request to move a piece. Generated by the
     * {@link #MOVE_AND_FIRE} command. */
    public void handleMoveAndFire (Object source, int[] data)
    {
//         log.info("Requesting move and fire: " + StringUtil.toString(data));
        BangService.InvocationListener il =
            new BangService.InvocationListener() {
            public void requestFailed (String reason) {
                // TODO: play a sound or highlight the piece that failed
                // to move
                log.info("Thwarted! " + reason);
            }
        };
        _bangobj.service.move(_ctx.getClient(), data[0], (short)data[1],
                              (short)data[2], data[3], il);
    }

    /** Handles a request to place a surprise. Generated by the
     * {@link * #PLACE_SURPRISE} command. */
    public void handlePlaceSurprise (Object source, int surpriseId)
    {
        Surprise s = (Surprise)_bangobj.surprises.get(surpriseId);
        if (s == null) {
            log.warning("Requested to place non-existent surprise '" +
                        surpriseId + "'.");
        } else {
            // instruct the board view to activate placement mode
            _view.view.placeSurprise(s);
        }
    }

    /** Handles a request to activate a surprise. Generated by the
     * {@link * #ACTIVATE_SURPRISE} command. */
    public void handleActivateSurprise (Object source, int[] data)
    {
        if (_bangobj.surprises.get(data[0]) == null) {
            log.warning("Requested to activate expired surprise '" +
                        StringUtil.toString(data) + "'.");
        } else {
            _bangobj.service.surprise(
                _ctx.getClient(), data[0], (short)data[1], (short)data[2]);
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
        if (state == BangObject.PRE_ROUND) {
            roundDidStart();
            return true;
        } else if (state == BangObject.POST_ROUND) {
            roundDidEnd();
            return true;
        } else {
            return super.handleStateChange(state);
        }
    }

    /**
     * Called when the round started, enters the pre-game, buying phase.
     */
    protected void roundDidStart ()
    {
        _view.buyingPhase(_bangobj, _config, _pidx);
    }

    @Override // documentation inherited
    protected void gameDidStart ()
    {
        super.gameDidStart();

        // we may be returning to an already started game
        _view.startGame(_bangobj, _config, _pidx);
    }

    /**
     * Called when the round ended, prepares to re-enter the buying phase.
     */
    protected void roundDidEnd ()
    {
        _view.view.endRound();
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
