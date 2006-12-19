//
// $Id$

package com.threerings.bang.game.server;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.logging.Level;

import com.samskivert.io.PersistenceException;
import com.samskivert.util.ArrayIntSet;
import com.samskivert.util.ArrayUtil;
import com.samskivert.util.HashIntMap;
import com.samskivert.util.IntIntMap;
import com.samskivert.util.IntListUtil;
import com.samskivert.util.IntSet;
import com.samskivert.util.Interval;
import com.samskivert.util.Invoker;
import com.samskivert.util.Multex;
import com.samskivert.util.RandomUtil;
import com.samskivert.util.ResultListener;
import com.samskivert.util.StringUtil;

import com.threerings.util.MessageBundle;
import com.threerings.util.StreamablePoint;

import com.threerings.presents.data.ClientObject;
import com.threerings.presents.dobj.AttributeChangeListener;
import com.threerings.presents.dobj.AttributeChangedEvent;
import com.threerings.presents.dobj.DSet;
import com.threerings.presents.server.InvocationException;
import com.threerings.presents.server.PresentsClient;
import com.threerings.presents.server.PresentsServer;

import com.threerings.crowd.chat.server.SpeakProvider;
import com.threerings.crowd.data.BodyObject;
import com.threerings.crowd.data.OccupantInfo;
import com.threerings.crowd.data.PlaceObject;
import com.threerings.crowd.server.PlaceManager;
import com.threerings.parlor.game.server.GameManager;

import com.threerings.bang.admin.data.StatusObject;
import com.threerings.bang.avatar.data.Look;
import com.threerings.bang.bounty.data.BountyConfig;

import com.threerings.bang.data.Badge;
import com.threerings.bang.data.BigShotItem;
import com.threerings.bang.data.CardItem;
import com.threerings.bang.data.PlayerObject;
import com.threerings.bang.data.Purse;
import com.threerings.bang.data.Rating;
import com.threerings.bang.data.Stat;
import com.threerings.bang.data.StatSet;
import com.threerings.bang.data.UnitConfig;
import com.threerings.bang.server.BangServer;
import com.threerings.bang.server.ServerConfig;
import com.threerings.bang.server.persist.BoardRecord;

import com.threerings.bang.game.data.Award;
import com.threerings.bang.game.data.BangAI;
import com.threerings.bang.game.data.BangBoard;
import com.threerings.bang.game.data.Criterion;
import com.threerings.bang.game.data.GameCodes;
import com.threerings.bang.game.data.card.Card;
import com.threerings.bang.game.data.effect.AddPieceEffect;
import com.threerings.bang.game.data.effect.AdjustTickEffect;
import com.threerings.bang.game.data.effect.BonusEffect;
import com.threerings.bang.game.data.effect.Effect;
import com.threerings.bang.game.data.effect.FailedShotEffect;
import com.threerings.bang.game.data.effect.HoldEffect;
import com.threerings.bang.game.data.effect.MoveEffect;
import com.threerings.bang.game.data.effect.MoveShootEffect;
import com.threerings.bang.game.data.effect.PlayCardEffect;
import com.threerings.bang.game.data.effect.ProximityShotEffect;
import com.threerings.bang.game.data.effect.ShotEffect;
import com.threerings.bang.game.data.effect.TeleportEffect;
import com.threerings.bang.game.data.piece.BigPiece;
import com.threerings.bang.game.data.piece.Bonus;
import com.threerings.bang.game.data.piece.Marker;
import com.threerings.bang.game.data.piece.Piece;
import com.threerings.bang.game.data.piece.Prop;
import com.threerings.bang.game.data.piece.Unit;

import com.threerings.bang.game.client.BangService;
import com.threerings.bang.game.data.BangConfig;
import com.threerings.bang.game.data.BangMarshaller;
import com.threerings.bang.game.data.BangObject;
import com.threerings.bang.game.data.BoardData;
import com.threerings.bang.game.data.ModifiableDSet;
import com.threerings.bang.game.data.TutorialCodes;
import com.threerings.bang.game.util.PieceSet;
import com.threerings.bang.game.util.PointSet;

import com.threerings.bang.game.data.scenario.PracticeInfo;
import com.threerings.bang.game.data.scenario.ScenarioInfo;
import com.threerings.bang.game.data.scenario.TutorialInfo;
import com.threerings.bang.game.server.ai.AILogic;
import com.threerings.bang.game.server.ai.PieceLogic;
import com.threerings.bang.game.server.scenario.Practice;
import com.threerings.bang.game.server.scenario.Scenario;
import com.threerings.bang.game.server.scenario.Tutorial;

import static com.threerings.bang.Log.log;

/**
 * Handles the server-side of the game.
 */
public class BangManager extends GameManager
    implements GameCodes, BangProvider
{
    /** Contains information on the players in the game which we need to ensure is around even if
     * the player logs off in the middle of the game. */
    public static class PlayerRecord
    {
        public int playerId, gangId;
        public Purse purse;
        public int[] finishedTick;
        public int readyState;

        public DSet<Rating> ratings;
        public HashMap<String,Rating> nratings = new HashMap<String,Rating>();

        public PlayerObject user;

        public Rating getRating (String scenario) {
            Rating rating = nratings.get(scenario);
            if (rating == null) {
                if (ratings != null) {
                    rating = ratings.get(scenario);
                }
                if (rating == null) {
                    rating = new Rating();
                    rating.scenario = scenario;
                } else if (rating.experience > 0) {
                    rating = (Rating)rating.clone();
                }
            }
            return rating;
        }
    }

    /** Used to rank the players at the end of the game. */
    public static class RankRecord implements Comparable<RankRecord>
    {
        public int pidx, points, kills, connected;

        public RankRecord (int pidx, int points, int kills, int connected) {
            this.pidx = pidx;
            this.points = points;
            this.kills = kills;
            this.connected = connected;
        }

        public int compareTo (RankRecord other) {
            int delta;
            if ((delta = (other.points - points)) != 0) {
                return delta;
            }
            if ((delta = (other.kills - kills)) != 0) {
                return delta;
            }
            if ((delta = (other.connected - connected)) != 0) {
                return delta;
            }
            return 0;
        }
    }

    /** Contains information about each round played. */
    public static class RoundRecord
    {
        /** The scenario used for the round. */
        public Scenario scenario;

        /** The last tick recorded for this round. */
        public int lastTick;

        /** The duration of this round in ticks. */
        public int duration;

        /** The metadata for the board we played on this round. */
        public BoardRecord board;

        /** The data for the board we played on this round. */
        public BoardData bdata;

        /** A snapshot of the in-game stats at the end of this round. */
        public StatSet[] stats;
    }

    /**
     * For game types that don't have variable sized teams, this returns the team size in effect
     * for all players. If you know what player you care about, use {@link #getTeamSize(int)}.
     */
    public int getTeamSize ()
    {
        return getTeamSize(0);
    }

    /**
     * Returns the team size for the current round.
     */
    public int getTeamSize (int pidx)
    {
        return (_bangobj.scenario == null) ?
            _bconfig.getTeamSize(pidx) : _bangobj.scenario.getTeamSize(_bconfig, pidx);
    }

    /**
     * Returns the player record at the specified index.
     */
    public PlayerRecord getPlayerRecord (int pidx)
    {
        return _precords[pidx];
    }

    /**
     * Called by the client when it has processed a particular tutorial action.  This is passed
     * through to the {@link Tutorial} scenario.
     */
    public void actionProcessed (PlayerObject caller, int actionId)
    {
        if (_scenario instanceof Tutorial) {
            ((Tutorial)_scenario).actionProcessed(caller, actionId);
        }
    }

    /**
     * If we are playing a bounty game, the manager will be configured with the appropriate bounty
     * configuration via this method.
     */
    public void setBountyConfig (BountyConfig bounty, String bountyGameId)
    {
        _bounty = bounty;
        _bountyGameId = bountyGameId;
    }

    /**
     * Returns a string describing the current board. Used for debug logging.
     */
    public String getBoardInfo ()
    {
        if (_rounds == null || _activeRoundId < 0 || _activeRoundId >= _rounds.length ||
            _bangobj == null || _bangobj.players == null) {
            return "unknown";
        }
        return _rounds[_activeRoundId].board.name + ":" + _bangobj.players.length;
    }

    // documentation inherited from interface BangProvider
    public void getBoard (ClientObject caller, BangService.BoardListener listener)
        throws InvocationException
    {
        PlayerObject user = (PlayerObject)caller;
        if (!_bangobj.occupants.contains(user.getOid())) {
            log.warning("Rejecting request for board by non-occupant [who=" + user.who() + "].");
            throw new InvocationException(INTERNAL_ERROR);
        } else if (_bangobj.board == null) {
            log.warning("Rejecting request for non-existent board [who=" + user.who() + "].");
            throw new InvocationException(INTERNAL_ERROR);
        }

        BoardRecord brec = _rounds[_activeRoundId].board;
        try {
            listener.requestProcessed(brec.getBoardData());
        } catch (IOException ioe) {
            log.log(Level.WARNING, "Failed to decode board " + brec + ".", ioe);
            throw new InvocationException(INTERNAL_ERROR);
        }
    }

    // documentation inherited from interface BangProvider
    public void selectTeam (ClientObject caller, int bigShotId, String[] units, int[] cardIds)
    {
        PlayerObject user = (PlayerObject)caller;
        int pidx = getPlayerIndex(user.getVisibleName());
        if (pidx == -1) {
            log.warning("Request to select team by non-player [who=" + user.who() + "].");
            return;
        }

        // make sure we're allow to select a team and that we haven't already done so
        if (_bconfig.type != BangConfig.Type.SALOON || _bangobj.bigShots[pidx] != null) {
            log.info("Rejecting repeat team selection [who=" + user.who() + "].");
            return;
        }

        // fetch the requisite items from their inventory
        Card[] cards = null;
        if (cardIds != null) {
            cards = new Card[cardIds.length];
            for (int ii = 0; ii < cardIds.length; ii++) {
                CardItem item = (CardItem)user.inventory.get(cardIds[ii]);
                // no magicking up cards
                if (item == null) {
                    continue;
                }
                int held = 0;
                for (Card heldCard : _bangobj.cards) {
                    if (heldCard.owner == pidx && !heldCard.found &&
                        heldCard.getType().equals(item.getType())) {
                        held++;
                    }
                }
                if (item.getQuantity() - held <= 0) {
                    continue;
                }
                // TODO: get pissy if they try to use the same card twice
                Card card = item.getCard();
                if (!card.isPlayable(_bangobj)) {
                    log.warning("Rejecting request to use nonplayable card [who=" + user.who() +
                                ", card=" + card + "].");
                    continue;
                }
                cards[ii] = card;
                cards[ii].init(_bangobj, pidx);
                cards[ii].found = false;
                _scards.put(cards[ii].cardId, new StartingCard(pidx, item));
            }
        }

        BigShotItem bsunit = (BigShotItem)user.inventory.get(bigShotId);
        // if something strange is going on with their big shot, give them a default
        if (bsunit == null) {
            bsunit = new BigShotItem(-1, "frontier_town/tactician");
        }
        selectTeam(user, pidx, bsunit, units, cards);

        // selecting a team implicitly reports one as ready for SELECT_PHASE (or rather its
        // completion)
        playerReadyFor(user, BangObject.SELECT_PHASE);
    }

    // documentation inherited from interface BangProvider
    public void order (ClientObject caller, int pieceId, short x, short y, int targetId,
                       BangService.ResultListener listener)
        throws InvocationException
    {
        if (_bangobj.state != BangObject.IN_PLAY) {
            throw new InvocationException(INTERNAL_ERROR);
        }

        PlayerObject user = (PlayerObject)caller;
        int pidx = getPlayerIndex(user.getVisibleName());

        if (!isActivePlayer(pidx)) {
            log.warning("Rejecting order from inactive player [pidx=" + pidx + "].");
            throw new InvocationException(INTERNAL_ERROR);
        }

        Piece piece = _bangobj.pieces.get(pieceId);
        if (piece == null || piece.owner != pidx) {
            // the unit probably died or was hijacked
            log.info("Rejecting order for invalid piece [who=" + user.who() + " (" + pidx + "), " +
                     "piece=" + piece + " (" + pieceId + ")].");
            throw new InvocationException(MOVER_NO_LONGER_VALID);
        }
        if (!(piece instanceof Unit)) {
            log.warning("Rejecting illegal move request [who=" + user.who() +
                        ", piece=" + piece + "].");
            throw new InvocationException(INTERNAL_ERROR);
        }

        Unit unit = (Unit)piece;
        int ticksTilMove = unit.ticksUntilMovable(_bangobj.tick);
        if (ticksTilMove > 0) {
            // make sure this new order is valid
            AdvanceOrder order = new AdvanceOrder(unit, x, y, targetId);
            String cause = order.checkValid();
            if (cause != null) {
                throw new InvocationException(cause);
            }

            // clear out any previous advance order for this unit
            clearOrders(unit.pieceId, false);

            // queue up our new advance order
            _orders.add(order);
            listener.requestProcessed(QUEUED_ORDER);

        } else {
            // execute the order immediately
            executeOrder(unit, x, y, targetId, true);
            listener.requestProcessed(EXECUTED_ORDER);
        }
    }

    // documentation inherited from interface BangProvider
    public void cancelOrder (ClientObject caller, int pieceId)
    {
        PlayerObject user = (PlayerObject)caller;
        int pidx = getPlayerIndex(user.getVisibleName());
        Piece piece = _bangobj.pieces.get(pieceId);
        if (piece == null || piece.owner != pidx) {
            // the unit probably died or was hijacked
            return;
        }
        clearOrders(pieceId, true);
    }

    // documentation inherited from interface BangProvider
    public void playCard (ClientObject caller, int cardId, Object target,
                          BangService.ConfirmListener listener)
        throws InvocationException
    {
        if (_bangobj.state != BangObject.IN_PLAY) {
            throw new InvocationException(INTERNAL_ERROR);
        }

        PlayerObject user = (PlayerObject)caller;
        Card card = _bangobj.cards.get(cardId);
        int pidx = getPlayerIndex(user.getVisibleName());
        if (card == null || card.owner != pidx || !isActivePlayer(pidx)) {
            log.warning("Rejecting invalid card request [who=" + user.who() + ", sid=" + cardId +
                        ", card=" + card + "].");
            throw new InvocationException(INTERNAL_ERROR);
        }

        // this is a little fiddly; we want to prepare the effect and make sure it's applicable
        // before removing the card
        Effect effect = card.activate(_bangobj, target);
        if (effect == null) {
            throw new InvocationException(CARD_UNPLAYABLE);
        }
        effect.prepare(_bangobj, _damage);
        if (!effect.isApplicable()) {
            _damage.clear();
            throw new InvocationException(CARD_UNPLAYABLE);
        }

        // play the played-card effect immediately before the card's actual effect
        log.info("Playing card: " + card);
        if (!deployEffect(card.owner, new PlayCardEffect(card, target))) {
            throw new InvocationException(CARD_UNPLAYABLE);
        }
        deployEffect(card.owner, effect, true);

        // if this card was a starting card, note that it was consumed
        StartingCard scard = _scards.get(cardId);
        if (scard != null) {
            scard.played = true;
        }

        // note that this player played a card
        _bangobj.stats[card.owner].incrementStat(Stat.Type.CARDS_PLAYED, 1);

        // let them know it worked
        listener.requestProcessed();
    }

    // documentation inherited from interface BangProvider
    public void reportPerformance (
        ClientObject caller, String board, String driver, int[] perfhisto)
    {
        PlayerObject user = (PlayerObject)caller;
        BangServer.perfLog("client_perf u:" + user.username + " b:" + board + " d:" + driver +
                           " h:" + StringUtil.toString(perfhisto));
    }

    /**
     * Called by the client when the player has completed some phase of the client initialization
     * and is ready to move to the next phase.
     */
    public void playerReadyFor (BodyObject caller, int state)
    {
        PlayerObject user = (PlayerObject)caller;
        int pidx = _bangobj.getPlayerIndex(user.handle);

        // if this is an all ai game, just do whatever the player is ready for
        if (_bconfig.allPlayersAIs()) {
            _bangobj.state = state;
            checkStartNextPhase();
            return;

        // if they're not an active player, ignore them
        } else if (!isActivePlayer(pidx)) {
            return;
        }

        // note their readiness state
        _precords[pidx].readyState = state;

        // now check to see if we should proceed
        checkStartNextPhase();
    }

    @Override // documentation inherited
    public void playerReady (BodyObject caller)
    {
        // if all players are AIs, the human observer determines when to proceed
        if (_bconfig.allPlayersAIs()) {
            playersAllHere();
        } else {
            super.playerReady(caller);
        }
    }

    /**
     * Attempts to move the specified unit to the specified coordinates and optionally fire upon
     * the specified target.
     *
     * @param unit the unit to be moved.
     * @param x the x coordinate to which to move or {@link Short#MAX_VALUE} if the unit should be
     * moved to the closest valid firing position to the target.
     * @param y the y coordinate to which to move, this is ignored if {@link Short#MAX_VALUE} is
     * supplied for x.
     * @param targetId the (optional) target to shoot after moving.
     * @param recheckOrders whether or not to recheck other advance orders after executing.
     */
    public void executeOrder (Unit unit, int x, int y, int targetId, boolean recheckOrders)
        throws InvocationException
    {
        try {
            _bangobj.startTransaction();

            // make sure the target is still around before we do anything
            Piece target = _bangobj.pieces.get(targetId);
            if (targetId > 0 && target == null) {
                throw new InvocationException(TARGET_NO_LONGER_VALID);
            }

            // TEMP: for debugging weird shot effect problems
            int dam1 = (target == null) ? 0 : target.damage;

            // if they specified a non-NOOP move, execute it
            int oldx = unit.x, oldy = unit.y;
            MoveEffect meffect = null;
            if (x != unit.x || y != unit.y) {
                meffect = moveUnit(unit, x, y, target);
            }

            if (meffect == null) {
                // check that our target is still valid and reachable
                checkTarget(unit, target, unit.x, unit.y);
            }

            // TEMP: for debugging weird shot effect problems
            int dam2 = (target == null) ? 0 : target.damage;

            // if they specified a target, shoot at it (we've already checked in moveUnit() or
            // above that our target is still valid)
            if (target != null) {
                ShotEffect effect;
                if (meffect == null || !(meffect instanceof MoveShootEffect)) {
                    // effect the initial shot
                    log.fine("Shooting " + target + " with " + unit);
                    effect = unit.shoot(_bangobj, target, 1f);
                    // the initial shot updates the shooter's last acted
                    effect.shooterLastActed = _bangobj.tick;

                    // apply the shot effect
                    if (!deployEffect(unit.owner, effect)) {
                        log.warning("Failed to deploy shot effect [unit=" + unit +
                                    ", move=" + x + "/" + y + ", target=" + target +
                                    ", dam1=" + dam1 + ", dam2=" + dam2+ "].");
                    } else if (unit.owner != -1) {
                        _bangobj.stats[unit.owner].incrementStat(Stat.Type.SHOTS_FIRED, 1);
                    }
                } else {
                    effect = ((MoveShootEffect)meffect).shotEffect;
                }
                _shooters.add(unit.pieceId);

                if (!(effect instanceof FailedShotEffect)) {
                    // effect any collateral damage
                    Effect[] ceffects = unit.collateralDamage(_bangobj, target, effect.newDamage);
                    int ccount = (ceffects == null) ? 0 : ceffects.length;
                    for (int ii = 0; ii < ccount; ii++) {
                        deployEffect(unit.owner, ceffects[ii]);
                    }

                    // allow the target to return fire on certain shots
                    if (!(effect instanceof ProximityShotEffect)) {
                        effect = target.returnFire(_bangobj, unit, effect.newDamage);
                    }
                    if (effect != null) {
                        deployEffect(target.owner, effect);
                        if (target.owner != -1) {
                            _bangobj.stats[target.owner].incrementStat(Stat.Type.SHOTS_FIRED, 1);
                        }
                    }
                }
            }

            if (unit.isAlive()) {
                // possibly deploy some post-shot effects
                for (Effect effect : _postShotEffects) {
                    deployEffect(unit.owner, effect);
                }

                // possibly deploy a post-order effect
                Effect peffect = unit.maybeGeneratePostOrderEffect();
                if (peffect != null) {
                    deployEffect(-1, peffect);
                }
            }
            _postShotEffects.clear();

            // finally update our metrics
            _bangobj.updateData();

        } finally {
            _bangobj.commitTransaction();
        }

        // finally, validate all of our advance orders and make sure none of them became invalid
        if (recheckOrders) {
            validateOrders();
        }
    }

    /**
     * Clears out all advance orders for all units.
     */
    public void clearOrders ()
    {
        for (AdvanceOrder order : _orders) {
            reportInvalidOrder(order, ORDER_CLEARED);
        }
        _orders.clear();
    }

    /**
     * Adds a piece to the board by deploying an {@link AddPieceEffect}.  The piece should already
     * have a valid piece id.
     */
    public void addPiece (Piece piece)
    {
        addPiece(piece, null);
    }

    /**
     * Adds a piece to the board by deploying an {@link AddPieceEffect}.  The piece should already
     * have a valid piece id.
     *
     * @param effect the effect to report on the piece after addition, or <code>null</code>.
     */
    public void addPiece (Piece piece, String effect)
    {
        deployEffect(-1, new AddPieceEffect(piece, effect));
    }

    /**
     * Prepares an effect and posts it to the game object, recording damage done in the process.
     *
     * @return true if the effect was deployed, false if the effect was either not applicable or
     * failed to apply.
     */
    public boolean deployEffect (int effector, Effect effect)
    {
        return deployEffect(effector, effect, false);
    }

    /**
     * Prepares an effect and posts it to the game object, recording damage done in the process.
     * Will also process any other effects that got queued for immediate deployment.
     *
     * @param prepared if true, the effect has already been prepared and determined to be
     * applicable.
     * @return true if the effect was deployed, false if the effect was either not applicable or
     * failed to apply.
     */
    public boolean deployEffect (int effector, Effect effect, boolean prepared)
    {
        boolean ret = actuallyDeployEffect(effector, effect, prepared);
        while (!_deployQueue.isEmpty()) {
            _deployQueue.removeFirst().deploy();
        }
        return ret;
    }

    /**
     * Queues an effect for deployment after the current effect finishes being applied.
     */
    public void queueDeployEffect (int effector, Effect effect, boolean prepared)
    {
        _deployQueue.add(new Deployable(effector, effect, prepared));
    }

    /**
     * Prepares an effect and posts it to the game object, recording damage done in the process.
     *
     * @param prepared if true, the effect has already been prepared and determined to be
     * applicable.
     * @return true if the effect was deployed, false if the effect was either not applicable or
     * failed to apply.
     */
    protected boolean actuallyDeployEffect (int effector, Effect effect, boolean prepared)
    {
        if (!prepared) {
            // prepare the effect
            effect.prepare(_bangobj, _damage);

            // make sure the effect is still applicable
            if (!effect.isApplicable()) {
                _damage.clear();
                if (effect instanceof BonusEffect && ((BonusEffect)effect).puntEffect != null) {
                    actuallyDeployEffect(effector, ((BonusEffect)effect).puntEffect, false);
                }
                return false;
            }
        }

        if (SYNC_DEBUG) {
            log.info("Applying effect " + effect);
        }

        // record our damage if appropriate
        if (effector != -1) {
            recordDamage(effector, _damage);
        } else {
            _damage.clear();
        }

        // broadcast the effect to the client
        _bangobj.setEffect((Effect)effect.clone());

        // on the server we apply the effect immediately
        return effect.apply(_bangobj, _effector);
    }

    /**
     * Called by the {@link Scenario} to start the specified phase of the game.
     */
    public void startPhase (int state)
    {
        // clear the pregame timer as we just switched phases
        _preGameTimer.cancel();

        switch (state) {
        case BangObject.SKIP_SELECT_PHASE:
            _bangobj.setState(BangObject.SKIP_SELECT_PHASE);
            break;

        case BangObject.SELECT_PHASE:
            // we may have to wait until we've noted the played cards
            _startRoundMultex.satisfied(Multex.CONDITION_ONE);
            break;

        case BangObject.IN_PLAY:
            startGame();
            break;

        default:
            log.warning("Unable to start next phase [game=" + where() + ", state=" + state + "].");
            Thread.dumpStack();
            break;
        }
    }

    @Override // documentation inherited
    public void updateOccupantInfo (OccupantInfo occInfo)
    {
        super.updateOccupantInfo(occInfo);

        // if an active player disconnected, boot them from the game
        int pidx = getPlayerIndex(occInfo.username);
        if (pidx != -1 && occInfo.status == OccupantInfo.DISCONNECTED &&
            _bangobj.isInPlay() && isActivePlayer(pidx)) {
            log.info("Booting disconnected player [game=" + where() +
                     ", who=" + occInfo.username + "].");
            endPlayerGame(pidx);
        }
    }

    @Override // documentation inherited
    public String where ()
    {
        return (_bangobj == null) ? super.where() :
            "[" + super.where() + ", board=" + getBoardInfo() + "]";
    }

    @Override // documentation inherited
    protected PlaceObject createPlaceObject ()
    {
        return new BangObject();
    }

    @Override // documentation inherited
    protected void didStartup ()
    {
        super.didStartup();
        log.info("Manager started up [where=" + where() + "].");

        // set up the bang object
        _bangobj = (BangObject)_gameobj;
        _bangobj.setService((BangMarshaller)PresentsServer.invmgr.registerDispatcher(
                                new BangDispatcher(this), false));
        _bangobj.addListener(_ticklst);
        _bconfig = (BangConfig)_gameconfig;

        // note this game in the status object
        StatusObject.GameInfo info = new StatusObject.GameInfo();
        info.gameOid = _bangobj.getOid();
        info.players = getPlayerCount();
        for (int ii = 0; ii < getPlayerCount(); ii++) {
            if (isAI(ii)) {
                info.players--;
            }
        }
        BangServer.adminmgr.statobj.addToGames(info);

        // note the time at which we started
        _startStamp = System.currentTimeMillis();

        // there are not saved cards to note before the first round, so start with that condition
        // already satisfied
        _startRoundMultex.satisfied(Multex.CONDITION_TWO);

        // ask the board manager to select or load up our boards
        ArrayIntSet prevIds = new ArrayIntSet();
        for (int ii = 0; ii < getPlayerCount(); ii++) {
            PlayerObject user = (PlayerObject)getPlayer(ii);
            if (user != null) {
                prevIds.add(user.lastBoardId);
            }
        }
        BoardRecord[] boards = BangServer.boardmgr.selectBoards(
            _bconfig.players.length, _bconfig.rounds, prevIds);

        // set up our round records
        _rounds = new RoundRecord[_bconfig.getRounds()];
        for (int ii = 0; ii < _rounds.length; ii++) {
            _rounds[ii] = new RoundRecord();
            _rounds[ii].board = boards[ii];
        }

        // configure the town associated with this server
        _bangobj.setTownId(ServerConfig.townId);

        // create our per-player arrays
        int slots = getPlayerSlots();
        _bangobj.points = new int[slots];
        _bangobj.perRoundPoints = new int[_bconfig.getRounds()][slots];
        _bangobj.perRoundRanks = new short[_bconfig.getRounds()][slots];
        for (int ii = 0; ii < _bangobj.perRoundPoints.length; ii++) {
            Arrays.fill(_bangobj.perRoundPoints[ii], -1);
        }
        _bangobj.pdata = new BangObject.PlayerData[slots];
        for (int ii = 0; ii < slots; ii++) {
            _bangobj.pdata[ii] = new BangObject.PlayerData();
        }
    }

    @Override // documentation inherited
    protected void didShutdown ()
    {
        super.didShutdown();
        PresentsServer.invmgr.clearDispatcher(_bangobj.service);
        BangServer.adminmgr.statobj.removeFromGames(_bangobj.getOid());
        log.info("Manager shutdown [where=" + where() + "].");
    }

    @Override // documentation inherited
    protected void playersAllHere ()
    {
        // if a player's client was crazy slow, they may have shown up and reported readiness after
        // the game already resigned them, in which case we may get an additional call to
        // playersAllHere() after the first one, we can just ignore it as the poor bastard will
        // just have to watch the game play out without him
        if (_precords != null) {
            return;
        }

        // create our player records now that we know everyone's in the room and ready to go
        _precords = new PlayerRecord[getPlayerSlots()];
        BangObject.PlayerInfo[] pinfo = new BangObject.PlayerInfo[getPlayerSlots()];
        for (int ii = 0; ii < _precords.length; ii++) {
            PlayerRecord prec = (_precords[ii] = new PlayerRecord());
            prec.finishedTick = new int[_bconfig.getRounds()];
            pinfo[ii] = new BangObject.PlayerInfo();
            if (isAI(ii)) {
                prec.playerId = -1;
                prec.ratings = new DSet<Rating>();
                pinfo[ii].avatar = ((BangAI)_AIs[ii]).avatar;
            } else if (isActivePlayer(ii)) {
                prec.user = (PlayerObject)getPlayer(ii);
                prec.playerId = prec.user.playerId;
                prec.gangId = prec.user.gangId;
                prec.purse = prec.user.getPurse();
                prec.ratings = prec.user.ratings;
                pinfo[ii].playerId = prec.user.playerId;
                Look look = prec.user.getLook(Look.Pose.DEFAULT);
                if (look != null) {
                    pinfo[ii].avatar = look.getAvatar(prec.user);
                }
            }
        }
        _bangobj.setPlayerInfo(pinfo);

        // when the players all arrive, go into the first game phase
        startRound();
    }

    @Override // documentation inherited
    protected void stateDidChange (int state, int oldState)
    {
        super.stateDidChange(state, oldState);

        // we do some custom additional stuff
        switch (state) {
        case BangObject.SELECT_PHASE:
            // select big shots, team and cards for our AIs
            for (int ii = 0; ii < getPlayerSlots(); ii++) {
                if (isAI(ii)) {
                    selectTeam(ii, _aiLogic[ii].getBigShotType(),
                               _aiLogic[ii].getUnitTypes(getTeamSize()),
                               _aiLogic[ii].getCardTypes());
                }
            }
            break;

        case BangObject.SKIP_SELECT_PHASE:
            // automatically select big shots, team and cards as appropriate
            if (_bconfig.type == BangConfig.Type.BOUNTY) {
                for (int ii = 0; ii < getPlayerSlots(); ii++) {
                    selectTeam(ii, _bconfig.teams.get(ii).bigShot, _bconfig.teams.get(ii).team,
                               _bconfig.teams.get(ii).cards);
                }
            }
            break;

        case BangObject.POST_ROUND:
            // do post-round processing and start the next one
            roundDidEnd(true);
            break;
        }
    }

    /** Starts the pre-game buying phase. */
    protected void startRound ()
    {
        _activeRoundId = _bangobj.roundId;
        // set the tick to -1 during the pre-round
        _bangobj.tick((short)-1);

        // set up our stats for this round
        StatSet[] stats = new StatSet[getPlayerSlots()];
        for (int ii = 0; ii < stats.length; ii++) {
            stats[ii] = new StatSet();
        }
        _rounds[_activeRoundId].stats = stats;
        _bangobj.stats = stats;

        // if this is a bounty game, set up a listener on the stat set so that we can broadcast the
        // values of our criterion related stats when they change
        if (_bconfig.type == BangConfig.Type.BOUNTY) {
            final HashSet<Stat.Type> sset = new HashSet<Stat.Type>();
            for (Criterion crit : _bconfig.criteria) {
                crit.addWatchedStats(sset);
            }
            _bangobj.setCritStats(new StatSet());
            stats[0].setContainer(new StatSet.Container() {
                public void addToStats (Stat stat) {
                    if (sset.contains(stat.getType())) {
                        _bangobj.addToCritStats(stat);
                    }
                }
                public void updateStats (Stat stat) {
                    if (sset.contains(stat.getType())) {
                        _bangobj.updateCritStats(stat);
                    }
                }
            });
        }

        // make sure we have a board at all
        final BoardRecord brec = _rounds[_activeRoundId].board;
        if (brec == null) {
            log.warning("Missing board, cannot start round [where=" + where() + "].");
            cancelGame();
            return;
        }

        // find out if the desired board has been loaded, loading it if not
        if (_rounds[_activeRoundId].bdata != null) {
            continueStartingRound();
            return;
        }

        BangServer.boardmgr.loadBoardData(
            brec, new ResultListener<BoardRecord>() {
            public void requestCompleted (BoardRecord record) {
                try {
                    _rounds[_activeRoundId].bdata = record.getBoardData();
                    continueStartingRound();
                } catch (IOException ioe) {
                    requestFailed(ioe);
                }
            }
            public void requestFailed (Exception cause) {
                log.log(Level.WARNING, "Failed to load or decode board data [brec=" + brec + "].",
                        cause);
                cancelGame();
            }
        });
    }

    /** Continues starting the round once the board's data is loaded. */
    protected void continueStartingRound ()
    {
        _bangobj.boardEffect = null;
        _bangobj.globalHindrance = null;

        // create the appropriate scenario to handle this round
        switch (_bconfig.type) {
        case TUTORIAL:
            _bangobj.setScenario(new TutorialInfo());
            _scenario = new Tutorial();
            break;

        case PRACTICE:
            _bangobj.setScenario(new PracticeInfo(ServerConfig.townId));
            _scenario = new Practice();
            break;

        default:
            ScenarioInfo info = ScenarioInfo.getScenarioInfo(_bconfig.getScenario(_activeRoundId));
            _bangobj.setScenario(info);
            String sclass = info.getScenarioClass();
            try {
                _scenario = (Scenario)Class.forName(sclass).newInstance();
            } catch (Exception e) {
                log.log(Level.WARNING, "Failed to instantiate scenario class: " + sclass, e);
                cancelGame();
                return;
            }
        }
        _scenario.init(this, _bangobj.scenario);

        RoundRecord round = _rounds[_activeRoundId];
        round.scenario = _scenario;

        // create the logic for our ai players, if any
        _aiLogic = new AILogic[(_AIs == null) ? 0 : _AIs.length];
        for (int ii = 0; ii < _aiLogic.length; ii++) {
            if (_AIs[ii] != null) {
                _aiLogic[ii] = _scenario.createAILogic(_AIs[ii]);
                _aiLogic[ii].init(this, ii);
            }
        }

        // set up the board and pieces so it's visible while purchasing
        _bangobj.board = (BangBoard)round.bdata.board.clone();
        if (_bounty != null) {
            _bangobj.setMarquee(_bounty.getName(_bountyGameId));
        } else {
            _bangobj.setMarquee(MessageBundle.taint(round.board.name));
        }
        _bangobj.setBoardHash(round.board.dataHash);

        // clone the pieces we get from the board as we may modify them during the game
        ArrayList<Piece> pieces = new ArrayList<Piece>();
        for (Piece p : round.bdata.pieces) {
            // sanity check our pieces
            if (p.x < 0 || p.x >= _bangobj.board.getWidth() ||
                p.y < 0 || p.y >= _bangobj.board.getHeight()) {
                log.warning("Out of bounds piece " + p + ".");
            } else {
                pieces.add((Piece)p.clone());
            }
        }

        // extract and remove all player start markers
        ArrayList<Piece> starts = new ArrayList<Piece>();
        for (Iterator<Piece> iter = pieces.iterator(); iter.hasNext(); ) {
            Piece p = iter.next();
            if (Marker.isMarker(p, Marker.START)) {
                if (p.isValidScenario(_bangobj.scenario.getIdent())) {
                    starts.add(p);
                }
                iter.remove();
            }
        }
        // if we lack sufficient numbers, freak out
        if (starts.size() < getPlayerSlots()) {
            log.warning("Board has insufficient start spots [game=" + where() +
                        ", need=" + getPlayerSlots() + "].");
            cancelGame();
            return;
        }

        // assign starting positions to the players
        _starts = new Piece[getPlayerSlots()];
        for (int tt = 0; tt < _bconfig.teams.size(); tt++) {
            // start at the desired location (which depends on the game config and game type)
            int stidx = _bconfig.teams.get(tt).startSpot;
            if (stidx == -1) {
                switch (_bconfig.type) {
                case SALOON: stidx = RandomUtil.getInt(starts.size()); break;
                default : stidx = tt; break;
                }
            }

            // then scan from there upwards looking for an unused starting spot
            for (int ii = 0, ll = starts.size(); ii < ll; ii++) {
                int idx = (stidx + ii) % ll;
                _starts[tt] = starts.get(idx);
                // stop when we find an unused starting spot
                if (_starts[tt] != null) {
                    starts.set(idx, null);
                    break;
                }
            }
        }

        // store them in the bang object for initial camera positions
        _bangobj.startPositions = new StreamablePoint[_starts.length];
        for (int ii = 0, nn = _starts.length; ii < nn; ii++) {
            _bangobj.startPositions[ii] = new StreamablePoint(_starts[ii].x, _starts[ii].y);
        }
        _bangobj.setStartPositions(_bangobj.startPositions);

        // give the scenario a shot at its own custom markers, updates
        ArrayList<Piece> updates = new ArrayList<Piece>();
        _scenario.filterPieces(_bangobj, _starts, pieces, updates);
        _bangobj.setBoardUpdates(updates.toArray(new Piece[updates.size()]));

        // remove any remaining marker pieces and assign piece ids; separate non-interactive props
        // from other pieces
        ArrayList<Prop> props = new ArrayList<Prop>();
        _bangobj.maxPieceId = 0;
        for (Iterator<Piece> iter = pieces.iterator(); iter.hasNext(); ) {
            Piece p = iter.next();
            if (p instanceof Marker && !((Marker)p).keepMarker()) {
                iter.remove();
                continue;
            }
            p.assignPieceId(_bangobj);
            p.init();
            if (p instanceof Prop && !((Prop)p).isInteractive()) {
                iter.remove();
                props.add((Prop)p);
            }
        }

        // configure the game object and board with the pieces
        _bangobj.props = props.toArray(new Prop[props.size()]);
        _bangobj.pieces = new ModifiableDSet<Piece>(pieces.iterator());
        _bangobj.board.shadowPieces(_bangobj.getPropPieceIterator());

        // clear out the selected big shots array
        _bangobj.setBigShots(new Unit[getPlayerSlots()]);

        // configure anyone who is not in the game room as resigned for this round
        for (int ii = 0; ii < getPlayerSlots(); ii++) {
            if (isAI(ii)) {
                continue;
            }
            PlayerObject user = (PlayerObject)getPlayer(ii);
            if (user == null || user.status == OccupantInfo.DISCONNECTED) {
                _bangobj.setPlayerStatusAt(BangObject.PLAYER_LEFT_GAME, ii);
            }
        }

        // transition to the pre-game selection phase
        if (_bconfig.type == BangConfig.Type.SALOON) {
            startPhase(BangObject.SELECT_PHASE);
        } else {
            startPhase(BangObject.SKIP_SELECT_PHASE);
        }
    }

    /**
     * Returns true if all active human players have reported as ready for the specified phase.
     */
    protected boolean allPlayersReadyFor (int phase)
    {
        for (int ii = 0; ii < _precords.length; ii++) {
            if (!isAI(ii) && isActivePlayer(ii) && _precords[ii].readyState < phase) {
                log.fine(getPlayer(ii) + " (" + ii + ") not ready for " + phase + ". Waiting.");
                return false;
            }
        }
        return true;
    }

    /**
     * Starts the select phase.
     */
    protected void startSelectPhase ()
    {
        _bangobj.setState(BangObject.SELECT_PHASE);
//        log.info("Starting select phase timer.");
//        _preGameTimer.state = BangObject.SELECT_PHASE;
//        _preGameTimer.schedule(15000L);
    }

    /**
     * Selects the starting team and configuration for an AI player or a human player in a
     * pre-configured (bounty) game.
     */
    protected void selectTeam (int pidx, String bigShotType, String[] units, String[] cardTypes)
    {
        Card[] cards = new Card[cardTypes != null ? cardTypes.length : 0];
        for (int ii = 0; ii < cards.length; ii++) {
            if (cardTypes[ii] != null) {
                cards[ii] = Card.newCard(cardTypes[ii]);
                cards[ii].init(_bangobj, pidx);
                cards[ii].found = false;
            }
        }
        BigShotItem bsitem = (bigShotType == null) ? null : new BigShotItem(-1, bigShotType);
        selectTeam((PlayerObject)getPlayer(pidx), pidx, bsitem, units, cards);
    }

    /**
     * Selects the starting team and configuration for this player.
     */
    protected void selectTeam (
        PlayerObject user, int pidx, BigShotItem item, String[] utypes, Card[] cards)
    {
        try {
            _bangobj.startTransaction();

            // if they supplied cards, store those to be added later
            if (cards != null) {
                for (int ii = 0; ii < cards.length; ii++) {
                    if (cards[ii] != null) {
                        _cardSet.add(cards[ii]);
                    }
                }
            }

            // configure their big shot selection
            if (item != null) {
                Unit unit = Unit.getUnit(item.getType());
                unit.assignPieceId(_bangobj);
                unit.init();
                unit.setOwner(_bangobj, pidx);
                unit.originalOwner = pidx;
                _bangobj.setBigShotsAt(unit, pidx);
            }

            // create an array of units from the requested types (limiting to the team size in case
            // they try to get sneaky)
            Unit[] units = new Unit[getTeamSize(pidx)];
            for (int ii = 0, ll = Math.min(units.length, utypes.length); ii < ll; ii++) {
                units[ii] = Unit.getUnit(utypes[ii]);
            }

            // if this is a human player and we're in a saloon game, make sure they didn't request
            // units to which they don't have access
            if (user != null && _bconfig.type == BangConfig.Type.SALOON) {
                for (int ii = 0; ii < units.length; ii++) {
                    if (units[ii] == null) {
                        continue;
                    }
                    UnitConfig config = units[ii].getConfig();
                    if (config != null && (config.scripCost < 0 || !config.hasAccess(user))) {
                        log.warning("Player requested to purchase illegal unit [who=" + user.who() +
                                    ", unit=" + config.type + "].");
                        units[ii] = null;
                    }
                }

                // TODO: make sure they didn't request more than their allowed number of each unit
                // (currently one)
            }

            // initialize and prepare the units
            initAndPrepareUnits(units, pidx);

        } finally {
            _bangobj.commitTransaction();
        }
    }

    /**
     * Utility method to initialize and prepare the units for a player.
     */
    public void initAndPrepareUnits (Unit[] units, int pidx)
    {
        for (int ii = 0; ii < units.length; ii++) {
            if (units[ii] != null) {
                initAndPrepareUnit(units[ii], pidx);
                _purchases.add(units[ii]);
            }
        }
    }

    /**
     * Utility method to initialize and prepare a unit for a player.
     */
    public void initAndPrepareUnit (Unit unit, int pidx)
    {
        unit.assignPieceId(_bangobj);
        unit.init();
        unit.setOwner(_bangobj, pidx);
        unit.originalOwner = pidx;
    }

    /**
     * Returns a set of pieceIds for pieces which shot this tick.
     */
    public IntSet getShooters ()
    {
        return _shooters;
    }

    /**
     * This is called when a player reports themselves as ready for a phase, or when a player is
     * removed from the game (in which case the next phase might need to be started because we were
     * waiting on that player).
     */
    protected void checkStartNextPhase ()
    {
        // if anyone has not yet reported readiness for this phase, we're not ready
        if (!allPlayersReadyFor(_bangobj.state)) {
            return;
        }

        log.fine("Starting next phase [cur=" + _bangobj.state + "].");
        switch (_bangobj.state) {
        case BangObject.SELECT_PHASE:
        case BangObject.SKIP_SELECT_PHASE:
            // add everyone's selected cards to the BangObject
            _bangobj.startTransaction();
            try {
                for (Card card : _cardSet) {
                    _bangobj.addToCards(card);
                }
            } finally {
                _bangobj.commitTransaction();
            }
            _cardSet.clear();
            startPhase(BangObject.IN_PLAY);
            break;

        case BangObject.IN_PLAY:
            // queue up the first board tick
            _ticker.schedule(_scenario.getTickTime(_bconfig, _bangobj), false);
            // let the players know we're ready to go with the first tick
            _bangobj.tick((short)0);
            break;

        default:
            log.warning("checkStartNextPhase() called during invalid phase! " +
                        "[where=" + where() + ", state=" + _bangobj.state + "].");
            break;
        }
    }

    @Override // documentation inherited
    protected void gameWillStart ()
    {
        super.gameWillStart();

        // add the selected big shots to the purchases
        for (int ii = 0; ii < _bangobj.bigShots.length; ii++) {
            if (isActivePlayer(ii) && _bangobj.bigShots[ii] != null) {
                _purchases.add(_bangobj.bigShots[ii]);
            }
        }

        // check and set some time-related statistics
        long now = System.currentTimeMillis();
        for (int ii = 0; ii < getPlayerCount(); ii++) {
            PlayerObject user = (PlayerObject)getPlayer(ii);
            if (user != null) {
                checkTimeStats(now, user);
            }
        }

        // now place and add the player pieces
        try {
            _bangobj.startTransaction();

            try {
                // let the scenario know that we're about to start the round
                _scenario.roundWillStart(_bangobj, _starts, _purchases);

                // configure the duration of the round
                int duration = _scenario.getDuration(_bconfig, _bangobj);
                // when testing multiple rounds it is useful to end games very quickly
                if (System.getProperty("quicktest") != null) {
                    duration /= 10;
                }
                _bangobj.setDuration((short)duration);
                _bangobj.setLastTick((short)(_bangobj.duration - 1));

                // note this round's duration for later processing
                _rounds[_activeRoundId].duration = _bangobj.duration;

            } catch (InvocationException ie) {
                log.warning("Scenario initialization failed [game=" + where() +
                            ", scen=" + _scenario + ", error=" + ie.getMessage() + "].");
                SpeakProvider.sendAttention(_bangobj, GAME_MSGS, ie.getMessage());
                // TODO: cancel the round (or let the scenario cancel it on the first tick?)
            }

            for (int ii = 0; ii < getPlayerSlots(); ii++) {
                // skip players that have abandoned ship
                if (!isActivePlayer(ii)) {
                    continue;
                }

                // note that this player is participating in this round by changing their
                // perRoundPoints from -1 to zero
                _bangobj.perRoundPoints[_activeRoundId][ii] = 0;

                // first filter out this player's pieces
                ArrayList<Piece> ppieces = new ArrayList<Piece>();
                for (Piece piece : _purchases.values()) {
                    if (piece.owner == ii) {
                        ppieces.add(piece);
                    }
                }

                // now position each of them
                ArrayList<Point> spots = _bangobj.board.getOccupiableSpots(
                    ppieces.size(), _starts[ii].x, _starts[ii].y, 4);
                while (spots.size() > 0 && ppieces.size() > 0) {
                    Point spot = spots.remove(0);
                    Piece piece = ppieces.remove(0);
                    piece.position(spot.x, spot.y);
                    addPiece(piece);
                }
            }

        } finally {
            _bangobj.commitTransaction();
        }
    }

    /**
     * Checks whether the player in question should have any of their time-related stats adjusted.
     */
    protected void checkTimeStats (long gameStart, PlayerObject user)
    {
        // get a calendar configured in the player's timezone
        PresentsClient client = BangServer.clmgr.getClient(user.username);
        if (client == null) {
            return;
        }
        Calendar cal = Calendar.getInstance(client.getTimeZone(), Locale.getDefault());
        cal.setTimeInMillis(gameStart);

        // check for high noon
        if (cal.get(Calendar.HOUR_OF_DAY) == 12 && cal.get(Calendar.MINUTE) == 0 &&
            cal.get(Calendar.SECOND) == 0) {
            user.stats.incrementStat(Stat.Type.MYSTERY_ONE, 1);
        }

        // check for christmas morning
        if (cal.get(Calendar.MONTH) == Calendar.DECEMBER &&
            cal.get(Calendar.DATE) == 25 && cal.get(Calendar.HOUR_OF_DAY) < 8) {
            user.stats.incrementStat(Stat.Type.MYSTERY_TWO, 1);
        }

        // TODO: night owl
    }

    /**
     * Called when the board tick is incremented.
     */
    protected void tick (short tick)
    {
        log.fine("Ticking [tick=" + tick + ", pcount=" + _bangobj.pieces.size() + "].");

        // allow pieces to tick down and possibly die
        Piece[] pieces = _bangobj.getPieceArray();
        for (int ii = 0; ii < pieces.length; ii++) {
            Piece p = pieces[ii];
            if (!p.isAlive()) {
                if (p.expireWreckage(tick)) {
                    log.fine("Expiring wreckage " + p.pieceId + " l:" + p.lastActed + " t:" + tick);
                    _bangobj.removeFromPieces(p.getKey());
                    _bangobj.board.clearShadow(p);
                }
            }

            int ox = p.x, oy = p.y;
            ArrayList<Effect> teffects = p.tick(tick, _bangobj, pieces);
            if (teffects != null) {
                for (Effect e : teffects) {
                    deployEffect(p.owner, e);
                }
            }
        }


        // note that all active players completed this tick
        _rounds[_activeRoundId].lastTick = tick;
        for (int ii = 0; ii < getPlayerSlots(); ii++) {
            if (isActivePlayer(ii)) {
                _precords[ii].finishedTick[_activeRoundId] = tick;
            }
        }

        // clear the set of shooters for this tick
        _shooters.clear();

        // execute any advance orders; we have to operate on a snapshot of the array because the
        // execution of one order may cause other advance orders to be cancelled and removed
        int executed = 0;
        AdvanceOrder[] aos = _orders.toArray(new AdvanceOrder[_orders.size()]);
        for (AdvanceOrder order : aos) {
            if (order.unit.ticksUntilMovable(tick) <= 0) {
                try {
                    executeOrder(order.unit, order.x, order.y, order.targetId, false);
                    executed++;
                } catch (InvocationException ie) {
                    reportInvalidOrder(order, ie.getMessage());
                }
                _orders.remove(order);
            }
        }

        // if we executed any orders, validate the remainder
        if (executed > 0) {
            validateOrders();
        }

        // give our AI players a chance to move but not on the zeroth tick
        if (_bconfig.type != BangConfig.Type.TUTORIAL && tick > 0) {
            for (int ii = 0; ii < _aiLogic.length; ii++) {
                if (_aiLogic[ii] != null) {
                    _aiLogic[ii].tick(pieces, tick);
                }
            }
            for (PieceLogic pl : _pLogics.values()) {
                pl.tick(pieces, tick);
            }
        }

        // tick the scenario which will do all the standard processing
        _scenario.tick(_bangobj, tick);

        // determine whether we should end the game
        if (tick >= _bangobj.lastTick) {
            // let the scenario do any end of round business
            _scenario.roundDidEnd(_bangobj);

            // if this is the last round, end the game
            if (_bangobj.roundId == _bconfig.getRounds()) {
                endGame();
            } else {
                endRound();
            }

            // cancel the board tick
            _ticker.cancel();
            return;
        }

        try {
            _bangobj.startTransaction();
            // potentially create and add new bonuses
            if (_scenario.addBonus(_bangobj, _bangobj.getPieceArray())) {
                _bangobj.updateData();
            }
        } finally {
            _bangobj.commitTransaction();
        }
    }

    protected void endRound ()
    {
        // trigger the display of the post round bits
        _bangobj.setState(BangObject.POST_ROUND);

        // clear out pending orders
        _orders.clear();
    }

    /**
     * Called when a round (or the whole game) ends, possibly starts up the next one.
     */
    protected void roundDidEnd (boolean startNext)
    {
        // record the consecutive kill counts of living units
        for (Piece piece : _bangobj.pieces) {
            if (piece.isAlive()) {
                recordConsecKills(piece);
            }
        }

        // broadcast our updated statistics
        _bangobj.setStats(_bangobj.stats);

        if (!(_bconfig.type == BangConfig.Type.PRACTICE ||
              _bconfig.type == BangConfig.Type.TUTORIAL)) {
            for (int ii = 0; ii < getPlayerCount(); ii++) {
                if (isAI(ii)) {
                    continue;
                }
                PlayerObject user = (PlayerObject)getPlayer(ii);
                if (user != null) {
                    try {
                        user.startTransaction();
                        user.setLastScenId(_bconfig.getScenario(_activeRoundId));
                        user.setLastBoardId(_rounds[_activeRoundId].board.boardId);
                    } finally {
                        user.commitTransaction();
                    }
                }
            }

            // calculate per round rankings
            RankRecord[] ranks = new RankRecord[_bangobj.points.length];
            for (int ii = 0; ii < ranks.length; ii++) {
                ranks[ii] = new RankRecord(
                    ii, _bangobj.perRoundPoints[_activeRoundId][ii],
                    _rounds[_activeRoundId].stats[ii].getIntStat(Stat.Type.UNITS_KILLED),
                    (isActivePlayer(ii) ? 1 : 0));
            }

            // we'll allow ties in the per round rankings
            Arrays.sort(ranks);
            short rank = 0;
            boolean coop = (_bangobj.scenario.getTeams() == ScenarioInfo.Teams.COOP);
            if (coop) {
                int avgscore = (IntListUtil.sum(_bangobj.points) / _bangobj.points.length);
                rank = (short)(BangObject.COOP_RANK + BangServer.ratingmgr.getPercentile(
                                   _bangobj.scenario.getIdent(), ranks.length, avgscore, false));
            }
            int high = _bangobj.points[ranks[0].pidx];
            for (int ii = 0; ii < ranks.length; ii++) {
                if (!coop) {
                    int points = _bangobj.points[ranks[ii].pidx];
                    if (points < high) {
                        rank = (short)ii;
                        high = points;
                    }
                }
                _bangobj.perRoundRanks[_activeRoundId][ranks[ii].pidx] = rank;
            }

            // record for all players still in the game that they "used" their units in this round
            noteUnitsUsed(_purchases, Stat.Type.UNITS_USED, -1);

            // also keep track of all big shot units used during the game
            for (Unit unit : _bangobj.bigShots) {
                if (unit != null) {
                    _bigshots.add(unit);
                }
            }
        }

        // clear out the various per-player data structures
        _purchases.clear();

        // process any played cards
        ArrayList<StartingCard> updates = new ArrayList<StartingCard>();
        ArrayList<StartingCard> removals = new ArrayList<StartingCard>();
        for (Iterator<StartingCard> iter = _scards.values().iterator(); iter.hasNext(); ) {
            StartingCard scard = iter.next();
            if (!scard.played) {
                continue;
            }
            if (scard.item.playCard()) {
                removals.add(scard);
            } else {
                updates.add(scard);
            }
            iter.remove();
        }
        if (updates.size() > 0 || removals.size() > 0) {
            notePlayedCards(updates, removals);
        } else {
            // we have no played cards to note, so there's no need to wait
            _startRoundMultex.satisfied(Multex.CONDITION_TWO);
        }

        // maybe start the next round
        if (startNext) {
            startRound();
        }
    }

    /**
     * Records and clears out the consecutive kill count of the specified piece, if it is a
     * player-owned unit.
     */
    protected void recordConsecKills (Piece piece)
    {
        if (piece.owner != -1 && piece instanceof Unit) {
            Unit unit = (Unit)piece;
            _bangobj.stats[piece.owner].maxStat(Stat.Type.CONSEC_KILLS,unit.consecKills);
            unit.consecKills = 0;
        }
    }

    @Override // documentation inherited
    protected void gameWasCancelled ()
    {
        super.gameWasCancelled();

        // record this game to the server stats log
        recordGame(null, 0);
    }

    @Override // documentation inherited
    protected void gameDidEnd ()
    {
        super.gameDidEnd();

        // do the normal round ending stuff as well
        roundDidEnd(false);

        // indicates whether we completed our bounty for the first time
        boolean completedBounty = false;

        PlayerObject user = (PlayerObject)getPlayer(0);
        if (user != null) {
            // if this was a tutorial practice session, and we played at least half of it, mark the
            // practice tutorial as completed
            if (_bconfig.duration == BangConfig.Duration.PRACTICE &&
                _bangobj.tick > _bangobj.duration/2) {
                user.stats.addToSetStat(Stat.Type.TUTORIALS_COMPLETED,
                                        TutorialCodes.PRACTICE_PREFIX + _bconfig.getScenario(0));
            }

            // TEMP: if this game had bounty criterion report whether they were met (by player
            // zero, who is the human player in a bounty game)
            if (_bconfig.type == BangConfig.Type.BOUNTY) {
                int failed = 0;
                for (Criterion crit : _bconfig.criteria) {
                    String msg = crit.isMet(_bangobj);
                    if (msg != null) {
                        failed++;
                        SpeakProvider.sendAttention(_bangobj, GAME_MSGS, msg);
                    } else {
                        SpeakProvider.sendAttention(_bangobj, GAME_MSGS, crit.reportMet(_bangobj));
                    }
                }
                if (failed == 0) {
                    SpeakProvider.sendAttention(_bangobj, GAME_MSGS, "m.all_criteria_met");

                    if (_bounty != null) {
                        user.stats.addToSetStat(Stat.Type.BOUNTY_GAMES_COMPLETED,
                                                _bounty.getStatKey(_bountyGameId));
                        if (!user.stats.containsValue(
                                Stat.Type.BOUNTIES_COMPLETED, _bounty.ident) &&
                            _bounty.isCompleted(user)) {
                            completedBounty = true;
                            user.stats.addToSetStat(Stat.Type.BOUNTIES_COMPLETED, _bounty.ident);
                        }
                    }
                }
            }
            // END TEMP
        }

        // note the duration of the game (in minutes and seconds)
        int gameSecs = (int)(System.currentTimeMillis() - _startStamp) / 1000;

        // update ratings if appropriate
        if (_bconfig.rated && !_bconfig.getScenario(0).equals(TutorialInfo.IDENT) &&
            gameSecs >= MIN_RATED_DURATION) {
            // update each player's per-scenario ratings
            for (int ii = 0; ii < _bconfig.getRounds(); ii++) {
                computeRatings(_bconfig.getScenario(ii), _bangobj.perRoundPoints[ii]);
            }

            // update each player's overall rating
            computeRatings(ScenarioInfo.OVERALL_IDENT, _bangobj.getFilteredPoints());
        }

        // these will track awarded cash and badges
        Award[] awards = new Award[getPlayerSlots()];

        // record various statistics
        for (int ii = 0; ii < awards.length; ii++) {
            Award award = (awards[ii] = new Award());
            award.pidx = ii;
            award.rank = -1;

            // note this player's rank
            if (_ranks != null) {
                for (int rr = 0; rr < _ranks.length; rr++) {
                    if (_ranks[rr].pidx == ii) {
                        award.rank = rr;
                        break;
                    }
                }
            }

            // stop here for non-humans
            if (isAI(ii)) {
                continue;
            }

            // if this player never showed up, skip the rest
            PlayerRecord prec = _precords[ii];
            if (prec.user == null) {
                continue;
            }

            // compute this player's "take home" cash
            if (prec.playerId > 0 && _scenario.shouldPayEarnings(prec.user)) {
                if (_bconfig.type == BangConfig.Type.BOUNTY) {
                    // if they completed a bounty, award them the bounty cash
                    if (completedBounty) {
                        award.cashEarned += _bounty.reward.scrip;
                        // TODO: handle other types of bounty prizes
                    }

                } else {
                    // compute their earnings and scale them based on the scenario duration
                    award.cashEarned = (int)Math.ceil(
                        computeEarnings(ii) * _bconfig.duration.getAdjustment());
                }
            }

            // if this was a rated game, persist various stats and potentially award a badge
            if (_bconfig.rated) {
                try {
                    recordStats(prec.user, ii, award, gameSecs/60);
                } catch (Throwable t) {
                    log.log(Level.WARNING, "Failed to record stats [who=" + _bangobj.players[ii] +
                            ", idx=" + ii + ", award=" + award + "].", t);
                }

            } else if (prec.user.isActive()) {
                // we only track a couple of stats for unranked games: the number played
                prec.user.stats.incrementStat(Stat.Type.UNRANKED_GAMES_PLAYED, 1);
                // the amount of cash earned
                prec.user.stats.incrementStat(Stat.Type.CASH_EARNED, award.cashEarned);
            }
        }

        // broadcast the per-round earnings which will be displayed on one stats panel
        _bangobj.setPerRoundPoints(_bangobj.perRoundPoints);
        _bangobj.setPerRoundRanks(_bangobj.perRoundRanks);

        // record this game to the server stats log (before we sort the awards)
        recordGame(awards, gameSecs);

        // sort by rank and then stuff the award data into the game object
        Arrays.sort(awards);
        _bangobj.setAwards(awards);

        // and persist the awards as well
        postGamePersist(awards);
    }

    @Override // documentation inherited
    protected void playerGameDidEnd (int pidx)
    {
        super.playerGameDidEnd(pidx);

        // if we haven't just lost our last human player, check if we should start the next phase
        if (getActiveHumanCount() > 0) {
            checkStartNextPhase();
        }
        // otherwise just let the game be ended or cancelled
    }

    @Override // documentation inherited
    protected boolean shouldEndGame ()
    {
        return _bangobj.isInPlay() &&
            (getActiveHumanCount() == 0 || _gameobj.getActivePlayerCount() == 1);
    }

    @Override // documentation inherited
    protected void assignWinners (boolean[] winners)
    {
        // compute the final ranking of each player, resolving ties using kill count, then a random
        // ordering
        int[] points = _bangobj.getFilteredPoints();
        _ranks = new RankRecord[points.length];
        for (int ii = 0; ii < _ranks.length; ii++) {
            int kills = 0;
            for (int rr = 0; rr < _rounds.length; rr++) {
                if (_rounds[rr].stats != null) {
                    kills += _rounds[rr].stats[ii].getIntStat(Stat.Type.UNITS_KILLED);
                }
            }
            _ranks[ii] = new RankRecord(ii, points[ii], kills, (isActivePlayer(ii) ? 1 : 0));
        }

        // first shuffle, then sort so that ties are resolved randomly
        ArrayUtil.shuffle(_ranks);
        Arrays.sort(_ranks);

        // now ensure that each player has at least one more point than the player ranked
        // immediately below them to communicate any last ditch tie resolution to the players
        for (int ii = _ranks.length-2; ii >= 0; ii--) {
            int highidx = _ranks[ii].pidx, lowidx = _ranks[ii+1].pidx;
            if (_bangobj.points[highidx] <= _bangobj.points[lowidx]) {
                _bangobj.setPointsAt(_bangobj.points[lowidx]+1, highidx);
                PlayerObject user = (PlayerObject)getPlayer(highidx);
                SpeakProvider.sendAttention(_bangobj, GAME_MSGS, MessageBundle.tcompose(
                            "m.tiebreaker_points", user.handle.toString()));
            }
        }

        // finally pass the winner info up to the parlor services
        winners[_ranks[0].pidx] = true;
    }

    /**
     * During the {@link BangObject#SELECT_PHASE} we set a timer that resigns any player that does
     * not make their selection or purchase within the alotted time frame.
     */
    protected void preGameTimerExpired (int targetState)
    {
        if (_bangobj.state != targetState) {
            // we may have expired at *just* the right time to miss the last player's submission in
            // which case we need do nothing
            return;
        }

        // resign anyone that has not selected a team
        for (int ii = 0; ii < getPlayerSlots(); ii++) {
            if (!isAI(ii) && _precords[ii].readyState == BangObject.SELECT_PHASE) {
                log.info("Player failed to make a selection in time [game=" + where() +
                         ", state=" + targetState + ", who=" + _bangobj.players[ii] + "].");
                endPlayerGame(ii);
            }
        }
    }

    /**
     * Attempts to move the specified piece to the specified coordinates.  Various checks are made
     * to ensure that it is a legal move.
     *
     * @return the cloned and moved piece if the piece was moved.
     */
    protected MoveEffect moveUnit (Unit unit, int x, int y, Piece target)
        throws InvocationException
    {
        // compute the possible moves for this unit
        _moves.clear();
        unit.computeMoves(_bangobj.board, _moves, null);

        // if we have not specified an exact move, locate one now
        if (x == Short.MAX_VALUE) {
            if (target == null) {
                // the target must no longer be around, so abandon ship
                throw new InvocationException(TARGET_NO_LONGER_VALID);
            }

            Point spot = unit.computeShotLocation(
                _bangobj.board, target, _moves, false);
            if (spot == null) {
//                 log.info("Unable to find place from which to shoot. [piece=" + unit +
//                          ", target=" + target + ", moves=" + _moves + "].");
                throw new InvocationException(TARGET_UNREACHABLE);
            }
            x = spot.x;
            y = spot.y;

            // if we decided not to move, just pretend like we did the job
            if (x == unit.x && y == unit.y && unit.getMaxFireDistance() > 0) {
                return null;
            }
        }

        // make sure we are alive, and are ready to move
        int steps = unit.getDistance(x, y);
        if (!unit.isAlive() || unit.ticksUntilMovable(_bangobj.tick) > 0) {
            log.info("Unit no longer movable [unit=" + unit + ", alive=" + unit.isAlive() +
                     ", mticks=" + unit.ticksUntilMovable(_bangobj.tick) + "].");
            throw new InvocationException(MOVER_NO_LONGER_VALID);
        }

        // validate that the move is still legal
        if (!_moves.contains(x, y) && x != unit.x && y != unit.y) {
//             log.info("Unit requested invalid move [unit=" + unit + ", x=" + x + ", y=" + y +
//                      ", moves=" + _moves + "].");
            throw new InvocationException(MOVE_BLOCKED);
        }

        // clone and move the unit
        Unit munit = (Unit)unit.clone();
        munit.position(x, y);
        munit.lastActed = _bangobj.tick;

        // ensure that we don't land on any piece that prevents us from overlapping
        boolean bridge = _bangobj.board.isBridge(x, y);
        ArrayList<Piece> lappers = _bangobj.getOverlappers(munit);
        if (lappers != null) {
            for (Piece lapper : lappers) {
                if (bridge && lapper instanceof BigPiece) {
                    continue;
                }
                if (lapper.preventsOverlap(munit) && lapper != unit) {
//                     log.info("Cannot overlap on move [unit=" + unit +
//                              ", x=" + x + ", y=" + y + "].");
                    throw new InvocationException(MOVE_BLOCKED);
                }
            }
        }

        // make sure we can still reach and shoot our target before we go ahead with our move
        checkTarget(unit, target, x, y);

        // update our board shadow
        _bangobj.board.clearShadow(unit);
        _bangobj.board.shadowPiece(munit);

        // record the move to this player's statistics
        if (unit.owner != -1) {
            _bangobj.stats[unit.owner].incrementStat(Stat.Type.DISTANCE_MOVED, steps);
        }

        // dispatch a move effect to actually move the unit
        MoveEffect meffect = unit.generateMoveEffect(_bangobj, x, y, target);
        _onTheMove = unit;
        if (deployEffect(unit.owner, meffect) && meffect instanceof MoveShootEffect &&
            unit.owner != -1) {
            _bangobj.stats[unit.owner].incrementStat(Stat.Type.SHOTS_FIRED, 1);
        }
        _onTheMove = null;

        // possibly generate a post-move effect
        Effect peffect = unit.maybeGeneratePostMoveEffect(steps);
        if (peffect != null) {
            deployEffect(-1, peffect);
        }

        // effects that resulted because of the move might prevent us from shooting our target
        if (!(meffect instanceof MoveShootEffect)) {
            checkTarget(unit, target, unit.x, unit.y);
        }

        return meffect;
    }

    /**
     * Checks that the specified unit can reach and shoot the specified target. Throws an
     * invocation exception if that is no longer the case (ie. the target moved out of range or
     * died). Target may be null in which case this method does nothing.
     */
    protected void checkTarget (Piece shooter, Piece target, int x, int y)
        throws InvocationException
    {
        if (target == null) {
            return;
        }

        // make sure the target is still valid
        if (!shooter.validTarget(_bangobj, target, false)) {
            // target already dead or something
//             log.info("Target no longer valid [shooter=" + shooter + ", target=" + target + "].");
            throw new InvocationException(TARGET_NO_LONGER_VALID);
        }

        // make sure the target is still reachable
        if (!shooter.targetInRange(x, y, target.x, target.y) ||
            !shooter.checkLineOfSight(_bangobj.board, x, y, target)) {
//             log.info("Target no longer reachable [shooter=" + shooter +
//                      ", target=" + target + "].");
            throw new InvocationException(TARGET_UNREACHABLE);
        }
    }

    /**
     * Scans the list of advance orders and clears any that have become invalid.
     */
    protected void validateOrders ()
    {
        for (Iterator<AdvanceOrder> iter = _orders.iterator(); iter.hasNext(); ) {
            AdvanceOrder order = iter.next();
            String cause = order.checkValid();
            if (cause != null) {
                iter.remove();
                reportInvalidOrder(order, cause);
            }
        }
    }

    /**
     * Immediately executes any pending order for the specified unit. This should be called when a
     * unit becomes ready out of the normal tick sequence, like via a Giddy Up card.
     */
    protected void executeOrders (int unitId)
    {
        AdvanceOrder order = null;
        for (int ii = 0, ll = _orders.size(); ii < ll; ii++) {
            if (_orders.get(ii).unit.pieceId == unitId) {
                order = _orders.remove(ii);
                break;
            }
        }
        if (order != null) {
            log.info("Immediately executing order " + order + ".");
            try {
                executeOrder(order.unit, order.x, order.y, order.targetId, true);
            } catch (InvocationException ie) {
                reportInvalidOrder(order, ie.getMessage());
            }
        }
    }

    /**
     * Clears any advance order for the specified unit.
     */
    protected void clearOrders (int unitId, boolean report)
    {
        for (int ii = 0, ll = _orders.size(); ii < ll; ii++) {
            AdvanceOrder order = _orders.get(ii);
            if (order.unit.pieceId == unitId) {
                if (report) {
                    reportInvalidOrder(order, ORDER_CLEARED);
                }
                _orders.remove(ii);
                return; // a unit will only have one outstanding order
            }
        }
    }

    /**
     * Reports an invalidated order to the initiating player.
     */
    protected void reportInvalidOrder (AdvanceOrder order, String reason)
    {
        PlayerObject user = (PlayerObject)getPlayer(order.unit.owner);
        if (user != null && user.status != OccupantInfo.DISCONNECTED) {
//             log.info("Advance order failed [order=" + order + ", who=" + user.who() + "].");
            BangSender.orderInvalidated(user, order.unit.pieceId, reason);
//         } else {
//             log.info("Advance order failed [order=" + order + "].");
        }
    }

    /** Records damage done by the specified user to various pieces. */
    protected void recordDamage (int pidx, IntIntMap damage)
    {
        int total = 0;
        for (int ii = -1; ii < getPlayerSlots(); ii++) {
            int ddone = damage.get(ii);
            if (ddone <= 0) {
                continue;
            }
            // deduct 150% if you shoot yourself; otherwise, give the scenario
            // a chance to modify the damage
            if (ii == pidx) {
                ddone = -3 * ddone / 2;
            } else {
                ddone = _scenario.modifyDamageDone(pidx, ii, ddone);
            }
            total += ddone;
        }

        // record the damage dealt statistic
        _bangobj.stats[pidx].incrementStat(Stat.Type.DAMAGE_DEALT, total);

        // award points for the damage dealt: 1 point for each 10 units
        total /= 10;
        _bangobj.grantPoints(pidx, total);

        // finally clear out the damage index
        damage.clear();
    }

    /**
     * Computes the take-home cash for the specified player index. This is based on their final
     * rank, their purse, the number of rounds played and the number of players.
     */
    protected int computeEarnings (int pidx)
    {
        // if we never set up our ranks, then no one gets nuthin
        if (_ranks == null) {
            return 0;
        }

        int earnings = 0;
        for (int rr = 0; rr < _bconfig.getRounds(); rr++) {
            // if the round was not played to at least half it's desired duration, skip it
            if (_rounds[rr].duration == 0 || _rounds[rr].lastTick < _rounds[rr].duration/2) {
                continue;
            }
            earnings += _rounds[rr].scenario.computeEarnings(
                _bangobj, pidx, rr, _precords, _ranks, _rounds);
        }

        // and scale earnings based on their purse
        return Math.round(_precords[pidx].purse.getPurseBonus() * earnings);
    }

    /**
     * Computes updated ratings for the specified scenario, using the supplied scores and stores
     * them in the appropriate {@link PlayerRecord}.
     */
    protected void computeRatings (String scenario, int[] scores)
    {
        // compute the average score for coop scenarios
        boolean coop = (!scenario.equals(ScenarioInfo.OVERALL_IDENT) &&
                        ScenarioInfo.getScenarioInfo(scenario).getTeams() ==
                        ScenarioInfo.Teams.COOP);
        int avgscore = coop ? (IntListUtil.sum(scores) / scores.length) : 0;

        // filter AIs from the scores; the ratings computations below will ignore players whose
        // score is set to zero
        scores = scores.clone();
        for (int ii = 0; ii < scores.length; ii++) {
            if (isAI(ii)) {
                scores[ii] = 0;
            }
        }

        // collect each player's rating for this scenario
        Rating[] ratings = new Rating[getPlayerSlots()];
        for (int pidx = 0; pidx < ratings.length; pidx++) {
            ratings[pidx] = _precords[pidx].getRating(scenario);
        }

        // now compute the adjusted ratings
        int pctile = coop ?
            BangServer.ratingmgr.getPercentile(scenario, ratings.length, avgscore, true) : 0;
        int[] nratings = new int[ratings.length];
        for (int pidx = 0; pidx < ratings.length; pidx++) {
            nratings[pidx] = coop ? Rating.computeCoopRating(pctile, ratings, pidx) :
                Rating.computeRating(scores, ratings, pidx);
        }

        // finally store the adjusted ratings back in the ratings objects and record the increased
        // experience
        for (int pidx = 0; pidx < ratings.length; pidx++) {
            // skip this rating if we weren't able to compute a value
            if (nratings[pidx] < 0) {
                continue;
            }
            ratings[pidx].rating = nratings[pidx];
            ratings[pidx].experience++;
            _precords[pidx].nratings.put(ratings[pidx].scenario, ratings[pidx]);
        }
    }

    /**
     * Records game stats to the player's persistent stats and potentially awards them a
     * badge. This is only called for rated (matched) games.
     */
    protected void recordStats (final PlayerObject user, int pidx, Award award, int gameMins)
    {
        // if this player has logged off...
        if (!user.isActive()) {
            // ...we won't update any of their cumulative stats, but we need to wipe their
            // consecutive wins stat
            BangServer.invoker.postUnit(new Invoker.Unit() {
                public boolean invoke () {
                    Stat stat = Stat.Type.CONSEC_WINS.newStat();
                    stat.setModified(true);
                    BangServer.statrepo.writeModified(user.playerId, new Stat[] { stat });
                    return false;
                }
            });
            return;
        }

        // send all the stat updates out in one dobj event
        user.startTransaction();

        try {
            // if the game wasn't sufficiently long, certain stats don't count
            if (gameMins >= MIN_STATS_DURATION) {
                user.stats.incrementStat(Stat.Type.GAMES_PLAYED, 1);
                user.stats.incrementStat(Stat.Type.SESSION_GAMES_PLAYED, 1);
                user.stats.incrementStat(Stat.Type.GAME_TIME, gameMins);
                // increment consecutive wins for 1st place only
                if (award.rank == 0) {
                    user.stats.incrementStat(Stat.Type.GAMES_WON, 1);
                    user.stats.incrementStat(Stat.Type.CONSEC_WINS, 1);
                    // note this win for all the big shots they used
                    noteUnitsUsed(_bigshots, Stat.Type.BIGSHOT_WINS, pidx);
                } else {
                    user.stats.setStat(Stat.Type.CONSEC_WINS, 0);
                }
                // increment consecutive losses for 4th place only
                if (award.rank == 3) {
                    user.stats.incrementStat(Stat.Type.CONSEC_LOSSES, 1);
                } else {
                    user.stats.setStat(Stat.Type.CONSEC_LOSSES, 0);
                }
            }

            // these stats count regardless of the game duration
            for (int rr = 0; rr < _rounds.length; rr++) {
                if (_rounds[rr].stats == null) {
                    continue; // skip unstarted rounds
                }

                // accumulate stats tracked during this round
                boolean competitive = _rounds[rr].scenario.getInfo().isCompetitive();
                for (int ss = 0; ss < ACCUM_STATS.length; ss++) {
                    Stat.Type type = ACCUM_STATS[ss];
                    // only track competitive stats for competitive rounds
                    if (type.isCompetitiveOnly() && !competitive) {
                        continue;
                    }
                    // we don't subtract accumulating stats if the player "accumulated" negative
                    // points in the game
                    int value = _rounds[rr].stats[pidx].getIntStat(type);
                    if (value > 0) {
                        user.stats.incrementStat(type, value);
                    }
                }

                // check to see if any "max" stat was exceeded in this round
                user.stats.maxStat(Stat.Type.HIGHEST_POINTS, _bangobj.perRoundPoints[rr][pidx]);
                for (int ss = 0; ss < MAX_STATS.length; ss += 2) {
                    Stat.Type type = MAX_STATS[ss+1];
                    // only track competitive stats for competitive rounds
                    if (type.isCompetitiveOnly() && !competitive) {
                        continue;
                    }
                    int v = _rounds[rr].stats[pidx].getIntStat(type);
                    user.stats.maxStat(MAX_STATS[ss], v);
                }
            }

            // note their cash earned
            user.stats.incrementStat(Stat.Type.CASH_EARNED, award.cashEarned);

            // allow the scenario to record statistics as well
            _scenario.recordStats(_bangobj, gameMins, pidx, user);

            // determine whether this player qualifies for a new badge
            award.badge = Badge.checkQualifies(user);

        } finally {
            user.commitTransaction();
        }
    }

    /**
     * A helper function for recording unit usage related stats.
     */
    protected void noteUnitsUsed (PieceSet units, Stat.Type stat, int pidx)
    {
        for (Piece piece : units.values()) {
            if (!(piece instanceof Unit) || (piece.owner < 0) ||
                (pidx != -1 && piece.owner != pidx)) {
                continue;
            }
            PlayerObject user;
            if (!isActivePlayer(piece.owner) ||
                (user = (PlayerObject)getPlayer(piece.owner)) == null) {
                continue;
            }
            user.stats.incrementMapStat(stat, ((Unit)piece).getType(), 1);
        }
    }

    /**
     * Records the relevant state of an ended or cancelled game.
     */
    protected void recordGame (Award[] awards, int gameSecs)
    {
        try {
            StringBuffer buf = new StringBuffer((awards == null) ? "game_cancelled" : "game_ended");
            buf.append(" t:").append(gameSecs);
            buf.append(" s:");
            for (int ii = 0; ii < _bconfig.rounds.size(); ii++) {
                if (ii > 0) {
                    buf.append(",");
                }
                buf.append(_bconfig.getScenario(ii));
            }
            buf.append(" ts:").append(getTeamSize());
            buf.append(" r:").append(_bconfig.rated);
            buf.append(" ");
            for (int ii = 0; ii < getPlayerSlots(); ii++) {
                if (ii > 0) {
                    buf.append(",");
                }

                // record the player in this position
                if (isAI(ii)) {
                    buf.append("(tin_can)");
                    continue;
                }
                if (_precords == null || _precords[ii] == null || _precords[ii].user == null) {
                    buf.append("(never_arrived)");
                    continue;
                }
                buf.append(_precords[ii].user.username);

                // note players that left the game early
                if (!isActivePlayer(ii)) {
                    PlayerObject pobj = BangServer.lookupPlayer(_precords[ii].user.handle);
                    if (pobj == null) {
                        buf.append("*"); // no longer online
                    } else if (pobj.status == OccupantInfo.DISCONNECTED) {
                        buf.append("!"); // disconnected
                    } else {
                        buf.append("#"); // online and active
                    }
                }

                // record their awards if we have any
                if (awards != null) {
                    buf.append(":").append(awards[ii]);
                }
            }
            BangServer.generalLog(buf.toString());

        } catch (Throwable t) {
            log.log(Level.WARNING, "Failed to log game data.", t);
        }
    }

    /**
     * Persists the supplied cash and badges and sticks them into the distributed objects of the
     * appropriate players. Also updates the players' ratings if appropriate.
     */
    protected void postGamePersist (final Award[] awards)
    {
        BangServer.invoker.postUnit(new Invoker.Unit() {
            public boolean invoke () {
                for (int pidx = 0; pidx < awards.length; pidx++) {
                    Award award = awards[pidx];
                    PlayerRecord prec = _precords[award.pidx];
                    if (prec.playerId < 0) {
                        continue; // skip AIs
                    }

                    // grant them their cash
                    if (award.cashEarned > 0) {
                        try {
                            BangServer.playrepo.grantScrip(prec.playerId, award.cashEarned);
                        } catch (PersistenceException pe) {
                            log.log(Level.WARNING, "Failed to award scrip [who=" + prec.playerId +
                                    ", scrip=" + award.cashEarned + "]", pe);
                        }
                    }

                    // grant them notoriety for their gang (same as scrip earned, for now)
                    int notoriety = award.cashEarned;
                    if (prec.gangId > 0 && notoriety > 0) {
                        try {
                            BangServer.gangrepo.addNotoriety(
                                prec.gangId, prec.playerId, notoriety);
                        } catch (PersistenceException pe) {
                            log.log(Level.WARNING, "Failed to grant notoriety [playerId=" +
                                prec.playerId + ", gangId=" + prec.gangId + ", notoriety=" +
                                notoriety + "].", pe);
                        }
                    }
                    
                    // grant them their badge
                    if (award.badge != null) {
                        try {
                            BangServer.itemrepo.insertItem(award.badge);
                        } catch (PersistenceException pe) {
                            log.log(Level.WARNING, "Failed to store badge " + award.badge, pe);
                        }
                    }

                    // update their ratings
                    if (prec.nratings.size() > 0) {
                        ArrayList<Rating> ratings = new ArrayList<Rating>(prec.nratings.values());
                        try {
                            BangServer.ratingrepo.updateRatings(prec.playerId, ratings);
                        } catch (PersistenceException pe) {
                            log.log(Level.WARNING, "Failed to persist ratings " +
                                    "[pid=" + prec.playerId +
                                    ", ratings=" + StringUtil.toString(ratings) + "]", pe);
                        }
                    }
                }
                return true;
            }

            public void handleResult () {
                for (int ii = 0; ii < awards.length; ii++) {
                    int pidx = awards[ii].pidx;
                    PlayerObject player = _precords[pidx].user;
                    if (player == null || !player.isActive()) {
                        // no need to update their player object if they've already logged off
                        continue;
                    }
                    if (awards[ii].cashEarned > 0) {
                        player.setScrip(player.scrip + awards[ii].cashEarned);
                    }
                    if (awards[ii].badge != null) {
                        player.addToInventory(awards[ii].badge);
                    }
                    for (Rating rating : _precords[pidx].nratings.values()) {
                        if (player.ratings.containsKey(rating.scenario)) {
                            player.updateRatings(rating);
                        } else {
                            player.addToRatings(rating);
                        }
                    }
                }
            }
        });
    }

    /**
     * Flushes any updated card items to the database and effects any removals due to the last card
     * being played from a player's inventory.
     */
    protected void notePlayedCards (final ArrayList<StartingCard> updates,
                                    final ArrayList<StartingCard> removals)
    {
        log.fine("Noting played cards [updates=" + updates.size() +
                 ", removals=" + removals.size() + "].");
        BangServer.invoker.postUnit(new Invoker.Unit() {
            public boolean invoke () {
                for (StartingCard scard : updates) {
                    try {
                        BangServer.itemrepo.updateItem(scard.item);
                    } catch (PersistenceException pe) {
                        log.log(Level.WARNING, "Failed to update played card " +
                                "[item=" + scard.item + "]", pe);
                    }
                }
                for (StartingCard scard : removals) {
                    try {
                        BangServer.itemrepo.deleteItem(scard.item, "played_last_card");
                    } catch (PersistenceException pe) {
                        log.log(Level.WARNING, "Failed to delete played card " +
                                "[item=" + scard.item + "]", pe);
                    }
                }
                return true;
            }

            public void handleResult () {
                // update the player's in-memory inventory
                for (StartingCard scard : updates) {
                    PlayerObject user = (PlayerObject)getPlayer(scard.pidx);
                    if (user != null) {
                        user.updateInventory(scard.item);
                    }
                }
                for (StartingCard scard : removals) {
                    PlayerObject user = (PlayerObject)getPlayer(scard.pidx);
                    if (user != null) {
                        user.removeFromInventory(scard.item.getKey());
                    }
                }

                // let the next round start if so desired
                _startRoundMultex.satisfied(Multex.CONDITION_TWO);
            }
        });
    }

    /**
     * Counts the number of active humans in the game.
     */
    protected int getActiveHumanCount ()
    {
        int humanCount = 0;
        for (int ii = 0; ii < getPlayerSlots(); ii++) {
            if (isActivePlayer(ii) && !isAI(ii)) {
                humanCount++;
            }
        }
        return humanCount;
    }

    /** Indicates that we're testing and to do wacky stuff. */
    protected boolean isTest ()
    {
        // if one of the AIs has a special personality code, we're testing
        int aicount = (_AIs == null) ? 0 : _AIs.length;
        for (int ii = 0; ii < aicount; ii++) {
            if (_AIs[ii] != null && _AIs[ii].personality == 1) {
                return true;
            }
        }
        return false;
    }

    /** Used to track advance orders. */
    protected class AdvanceOrder
    {
        /** The unit to be ordered. */
        public Unit unit;

        /** The coordinates to which to move the unit. */
        public short x, y;

        /** The target to attack after moving. */
        public int targetId;

        public AdvanceOrder (Unit unit, short x, short y, int targetId) {
            this.unit = unit;
            this.x = x;
            this.y = y;
            this.targetId = targetId;
        }

        public String checkValid () {
            // sanity check, though I think this bug is fixed
            Object obj = _bangobj.pieces.get(unit.pieceId);
            if (obj != null && !(obj instanceof Unit)) {
                log.warning("Our unit became a non-unit!? [where=" + where() +
                            ", unit=" + unit + ", nunit=" + obj + "].");
                return INTERNAL_ERROR;
            }

            // make sure this unit is still in play
            Unit aunit = (Unit)obj;
            if (aunit == null || !aunit.isAlive()) {
                log.info("Advance order no longer valid [order=" + this + ", unit=" +
                         (aunit == null ? "null" : (aunit + " (" + aunit.isAlive() + ")")) + "].");
                return MOVER_NO_LONGER_VALID;
            }

            // make sure our target is still around
            Piece target = null;
            if (targetId > 0) {
                target = _bangobj.pieces.get(targetId);
                if (target == null || !target.isAlive()) {
                    return TARGET_NO_LONGER_VALID;
                }
            }

            // compute our potential move and attack set
            _moves.clear();
            _attacks.clear();
            unit.computeMoves(_bangobj.board, _moves, null);

            // if no specific location was specified, make sure we can still determine a location
            // from which to fire
            if (x == Short.MAX_VALUE) {
                if (target == null) { // sanity check
                    return TARGET_NO_LONGER_VALID;
                }
                return (unit.computeShotLocation(_bangobj.board, target, _moves, true) == null) ?
                    TARGET_UNREACHABLE : null;
            }

            // if a specific location was specified, make sure we can still reach it
            if (!_moves.contains(x, y)) {
                return MOVE_BLOCKED;
            }

            // if we have no target, we're good to go
            if (target == null) {
                return null;
            }

            // we are doing a move and shoot, so make sure we can still hit the target from our
            // desired move location
            if (!unit.targetInRange(x, y, target.x, target.y) ||
                !unit.checkLineOfSight(_bangobj.board, x, y, target)) {
                return TARGET_UNREACHABLE;
            }

            return null;
        }

        public String toString() {
            return unit + " -> +" + x + "+" + y + " (" + targetId + ")";
        }
    }

    /** Used to track cards from a player's inventory and whether or not they are actually used
     * during a game. */
    protected static class StartingCard
    {
        public int pidx;
        public CardItem item;
        public boolean played;

        public StartingCard (int pidx, CardItem item) {
            this.pidx = pidx;
            this.item = item;
        }
    }

    /** Used to time out players that don't make a pre-game selection. */
    protected class PreGameTimer extends Interval
    {
        /** The state of the game when the timer was started. */
        public int state;

        public PreGameTimer () {
            super(BangServer.omgr);
        }

        public void expired () {
            preGameTimerExpired(state);
        }
    }

    /** Holds the information for deploying an effect. */
    protected class Deployable
    {
        public int effector;
        public Effect effect;
        public boolean prepared;

        public Deployable (int effector, Effect effect, boolean prepared) {
            this.effector = effector;
            this.effect = effect;
            this.prepared = prepared;
        }

        public void deploy () {
            actuallyDeployEffect(effector, effect, prepared);
        }
    }

    /** Triggers our board tick once every N seconds. */
    protected Interval _ticker = _ticker = new Interval(PresentsServer.omgr) {
        public void expired () {
            // cope if the game has been ended and destroyed since we were queued up for execution
            if (!_bangobj.isActive() || _bangobj.state != BangObject.IN_PLAY) {
                return;
            }

            // reset the extra tick time and update the game's tick counter
            int nextTick = (_bangobj.tick + 1) % Short.MAX_VALUE;
            _extraTickTime = 0L;
            _bangobj.tick((short)nextTick);

            // queue up the next tick
            long tickTime = (long)Math.round(_scenario.getTickTime(_bconfig, _bangobj) *
                                             _bconfig.speed.getAdjustment());
            tickTime += _extraTickTime;
            _ticker.schedule(tickTime);
            _nextTickTime = System.currentTimeMillis() + tickTime;
        }
    };

    /** Handles post-processing when effects are applied. */
    protected Effect.Observer _effector = new Effect.Observer() {
        public void pieceAdded (Piece piece) {
            String pieceLogic = piece.getLogic();
            if (pieceLogic != null) {
                try {
                    PieceLogic plogic = (PieceLogic)Class.forName(pieceLogic).newInstance();
                    plogic.init(BangManager.this, piece);
                    _pLogics.put(piece.pieceId, plogic);
                } catch (Exception e) {
                    log.log(Level.WARNING, "Failed to create piece logic " +
                            "[piece=" + piece + ", class=" + pieceLogic + "].", e);
                }
            }
        }

        public void pieceAffected (Piece piece, String effect) {
            if (effect.equals(AdjustTickEffect.GIDDY_UPPED) &&
                piece.ticksUntilMovable(_bangobj.tick) == 0) {
                // if a piece was giddy upped into readiness, immediately execute any advance order
                // it has registered
                executeOrders(piece.pieceId);
            } else if (HoldEffect.isDroppedEffect(effect)) {
                // if a piece dropped its held bonus, cancel any advance order it has registered
                clearOrders(piece.pieceId, true);
            }
            _scenario.pieceAffected(piece, effect);
        }

        public void boardAffected (String effect) {
        }

        public void pieceMoved (final Piece piece) {
            // first, interact with any pieces occupying our target space
            ArrayList<Piece> lappers = _bangobj.getOverlappers(piece);
            if (lappers != null) {
                for (Piece lapper : lappers) {
                    Effect[] effects = piece.maybeInteract(_bangobj, lapper);
                    if (effects == null || effects.length == 0) {
                        continue;
                    }

                    for (Effect effect : effects) {
                        if (effect == null) {
                            continue;
                        } else if (_onTheMove == piece && effect instanceof TeleportEffect) {
                            _postShotEffects.add(effect);
                            continue;
                        } else {
                            queueDeployEffect(piece.owner, effect, false);
                        }

                        // small hackery: note that this player collected a bonus
                        if (piece.owner != -1 && effect instanceof HoldEffect &&
                            ((HoldEffect)effect).dropping == false && lapper instanceof Bonus &&
                            !((Bonus)lapper).isScenarioBonus()) {
                            _bangobj.stats[piece.owner].incrementStat(
                                Stat.Type.BONUSES_COLLECTED, 1);
                        }
                    }
                }
            }

            // once the effects generated by this move have been deployed we can let the scenario
            // know that the unit moved which may result in nuggets stolen, cattle rustled, etc.
            _deployQueue.add(new Deployable(-1, null, false) {
                public void deploy () {
                    _scenario.pieceMoved(_bangobj, piece);
                }
            });
        }

        public void pieceKilled (Piece piece, int shooter) {
            // queue a post death effect
            Effect effect = piece.didDie(_bangobj);
            if (effect != null) {
                queueDeployEffect(piece.owner, effect, false);
            }

            // let the scenario know that the piece was killed
            _scenario.pieceWasKilled(_bangobj, piece, shooter);

            // if this is a unit and owned by a player, update their consecutive kills
            recordConsecKills(piece);
        }

        public void pieceRemoved (Piece piece) {
            _pLogics.remove(piece.pieceId);
        }

        public void cardAdded (Card card) {
        }

        public void cardRemoved (Card card) {
        }

        public void cardPlayed (Card card, Object target) {
        }

        public void tickDelayed (long extraTime) {
            // if we are currently processing a tick, add to the extra tick time; otherwise,
            // postpone the next tick
            long now = System.currentTimeMillis();
            if (now >= _nextTickTime) {
                _extraTickTime = Math.max(_extraTickTime, extraTime);
            } else {
                _nextTickTime += extraTime;
                _ticker.schedule(_nextTickTime - now);
            }
        }
    };

    /** Dispatches calls to {@link #tick} when the tick changes. */
    protected AttributeChangeListener _ticklst = new AttributeChangeListener() {
        public void attributeChanged (AttributeChangedEvent event) {
            if (event.getName().equals(BangObject.TICK)) {
                if (_bangobj.tick >= 0) {
                    tick(_bangobj.tick);
                }
            }
        }
    };

    /** Used to coordinate the starting of a round. */
    protected Multex _startRoundMultex = new Multex(new Runnable() {
        public void run () {
            startSelectPhase();
        }
    }, 2);

    /** A casted reference to our game configuration. */
    protected BangConfig _bconfig;

    /** A casted reference to our game object. */
    protected BangObject _bangobj;

    /** Our bounty configuration if this is a bounty game, null otherwise. */
    protected BountyConfig _bounty;

    /** The id of the bounty game we're playing if this is a bounty game, null otherwise. */
    protected String _bountyGameId;

    /** Contains info on each round that we played. */
    protected RoundRecord[] _rounds;

    /** Contains info on all of the players in the game. */
    protected PlayerRecord[] _precords;

    /** Used at the end of the game to rank the players. */
    protected RankRecord[] _ranks;

    /** Implements our gameplay scenario. */
    protected Scenario _scenario;

    /** The logic for the artificial players. */
    protected AILogic[] _aiLogic;

    /** The time at which the round started. */
    protected long _startStamp;

    /** The purchases made by players in the buying phase. */
    protected PieceSet _purchases = new PieceSet();

    /** The Big Shots used by the players during all rounds of this game. */
    protected PieceSet _bigshots = new PieceSet();

    /** Used to record damage done during an attack. */
    protected IntIntMap _damage = new IntIntMap();

    /** Used to compute a piece's potential moves or attacks when validating a move request. */
    protected PointSet _moves = new PointSet(), _attacks = new PointSet();

    /** Used to resign players if they do not make a selection during the pre-game phase. */
    protected PreGameTimer _preGameTimer = new PreGameTimer();

    /** Used to track the locations where players can start. */
    protected Piece[] _starts;

    /** Maps card id to a {@link StartingCard} record. */
    protected HashIntMap<StartingCard> _scards = new HashIntMap<StartingCard>();

    /** Stores cards to be added to the BangObject. */
    protected HashSet<Card> _cardSet = new HashSet<Card>();

    /** The time for which the next tick is scheduled. */
    protected long _nextTickTime;

    /** The extra time to take for the current tick to allow extended effects to complete. */
    protected long _extraTickTime;

    /** Store the round id here as the BangObject doesn't track it the way we want. */
    protected int _activeRoundId;

    /** Marks a piece currently in a move from moveUnit. */
    protected Piece _onTheMove;

    /** A list of effects to do after the shooting has stopped. */
    protected ArrayList<Effect> _postShotEffects = new ArrayList<Effect>();

    /** A queue of effects to be deployed. */
    protected LinkedList<Deployable> _deployQueue = new LinkedList<Deployable>();

    /** A set of units which shot this tick. */
    protected ArrayIntSet _shooters = new ArrayIntSet();

    /** A mapping of pieceIds to specilized logic handlers. */
    protected HashIntMap<PieceLogic> _pLogics = new HashIntMap<PieceLogic>();

    /** Tracks advance orders. */
    protected ArrayList<AdvanceOrder> _orders = new ArrayList<AdvanceOrder>();

    /** If a game is shorter than this (in seconds) we won't rate it. */
    protected static final int MIN_RATED_DURATION = 180;

    /** If a game is shorter than this (in minutes) some stats don't count. */
    protected static final int MIN_STATS_DURATION = 2;

    /** Stats that we accumulate at the end of the game into the player's persistent stats. */
    protected static final Stat.Type[] ACCUM_STATS = {
        Stat.Type.UNITS_KILLED,
        Stat.Type.UNITS_LOST,
        Stat.Type.BONUSES_COLLECTED,
        Stat.Type.CARDS_PLAYED,
        Stat.Type.POINTS_EARNED,
        Stat.Type.SHOTS_FIRED,
        Stat.Type.DISTANCE_MOVED,
    };

    /** Stats that we max() at the end of the game into the player's persistent stats. */
    protected static final Stat.Type[] MAX_STATS = {
        Stat.Type.CONSEC_KILLS, Stat.Type.CONSEC_KILLS,
    };
}
