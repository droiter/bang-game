//
// $Id$

package com.threerings.bang.tests.game;

import java.util.logging.Level;

import com.jme.util.LoggingSystem;

import com.jmex.bui.BWindow;

import com.samskivert.util.RandomUtil;
import com.threerings.util.Name;

import com.threerings.bang.data.Badge;
import com.threerings.bang.data.Handle;
import com.threerings.bang.data.Item;
import com.threerings.bang.data.PlayerObject;
import com.threerings.bang.data.Purse;
import com.threerings.bang.data.Stat;
import com.threerings.bang.data.StatSet;

import com.threerings.bang.game.client.GameOverView;
import com.threerings.bang.game.client.StatsView;

import com.threerings.bang.game.data.Award;
import com.threerings.bang.game.data.BangAI;
import com.threerings.bang.game.data.BangObject;
import com.threerings.bang.game.data.ScenarioCodes;

import com.threerings.bang.tests.TestApp;

import com.threerings.presents.dobj.DSet;

/**
 * Test harness for the game over view.
 */
public class StatsViewTest extends TestApp
{
    public static void main (String[] args)
    {
        LoggingSystem.getLogger().setLevel(Level.WARNING);
        StatsViewTest test = new StatsViewTest();
        if (test.init()) {
            test.initTest();
            test.run();
        } else {
            System.exit(-1);
        }
    }

    protected BWindow createWindow ()
    {
        PlayerObject user = new PlayerObject();
        user.handle = new Handle("Wild Annie");
        user.inventory = new DSet<Item>(new Purse[] { new Purse(-1, 1) });
        user.scrip = 125378;

        BangObject bangobj = new BangObject();
        bangobj.players = new Name[] {
            new Name("Scary Jerry"),
            new Name("Monkey Butter"),
            user.handle,
            new Name("Elvis"),
        };
        bangobj.avatars = new int[bangobj.players.length][];
        bangobj.awards = new Award[bangobj.players.length];
        bangobj.stats = new StatSet[bangobj.players.length];
        bangobj.roundId = 1;
        bangobj.state = BangObject.GAME_OVER;
        for (int ii = 0; ii < bangobj.awards.length; ii++) {
            bangobj.awards[ii] = new Award();
            bangobj.awards[ii].pidx = bangobj.awards.length-ii-1;
            if (bangobj.awards[ii].pidx == 2) {
                bangobj.awards[ii].badge =
                    Badge.Type.DISTANCE_MOVED_1.newBadge();
            }
            bangobj.awards[ii].rank = ii;
            bangobj.awards[ii].cashEarned = 100;
            bangobj.avatars[ii] = BangAI.getAvatarPrint(
                RandomUtil.getInt(100) > 50);
            bangobj.stats[ii] = new StatSet();
            bangobj.stats[ii].setStat(Stat.Type.CATTLE_RUSTLED,
                    RandomUtil.getInt(5) * ii);
            bangobj.stats[ii].setStat(Stat.Type.NUGGETS_CLAIMED, 
                    RandomUtil.getInt(10));
            bangobj.stats[ii].setStat(Stat.Type.DAMAGE_DEALT,
                    RandomUtil.getInt(500));
            bangobj.stats[ii].setStat(Stat.Type.POINTS_EARNED,
                    RandomUtil.getInt(2500, 1000));
            bangobj.stats[ii].setStat(Stat.Type.DAMAGE_DEALT,
                    RandomUtil.getInt(500));
            bangobj.stats[ii].setStat(Stat.Type.UNITS_KILLED,
                    RandomUtil.getInt(15));
            bangobj.stats[ii].setStat(Stat.Type.UNITS_LOST,
                    RandomUtil.getInt(15));
            bangobj.stats[ii].setStat(Stat.Type.BONUSES_COLLECTED,
                    RandomUtil.getInt(7));
            bangobj.stats[ii].setStat(Stat.Type.CARDS_PLAYED,
                    RandomUtil.getInt(6));
            bangobj.stats[ii].setStat(Stat.Type.DISTANCE_MOVED,
                    RandomUtil.getInt(250));
            bangobj.stats[ii].setStat(Stat.Type.SHOTS_FIRED,
                    RandomUtil.getInt(50));
            bangobj.stats[ii].setStat(Stat.Type.BRAND_POINTS,
                    RandomUtil.getInt(400));
        }
        bangobj.scenarioId = ScenarioCodes.CATTLE_RUSTLING;
        //bangobj.scenarioId = ScenarioCodes.CLAIM_JUMPING;
        

        return new StatsView(_ctx, null, bangobj, bangobj.scenarioId, true);
    }
}
