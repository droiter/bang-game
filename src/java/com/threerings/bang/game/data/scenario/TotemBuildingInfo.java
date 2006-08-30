//
// $Id$

package com.threerings.bang.game.data.scenario;

import com.threerings.bang.data.BangCodes;
import com.threerings.bang.data.Stat;
import com.threerings.bang.util.BasicContext;

import com.threerings.bang.game.client.StatsView;
import com.threerings.bang.game.client.TotemStatsView;
import com.threerings.bang.game.data.piece.Marker;
import com.threerings.bang.game.data.piece.TotemBonus;

/**
 * Contains metadata on the Totem Building scenario.
 */
public class TotemBuildingInfo extends ScenarioInfo
{
    /** The string identifier for this scenario. */
    public static final String IDENT = "tb";

    /** Points earned for each totem piece. */
    public static final int POINTS_PER_TOTEM = 25;

    @Override // from ScenarioInfo
    public String getIdent ()
    {
        return IDENT;
    }

    @Override // from ScenarioInfo
    public String getTownId ()
    {
        return BangCodes.INDIAN_POST;
    }

    @Override // from ScenarioInfo
    public Stat.Type[] getObjectives ()
    {
        return new Stat.Type[] {
            Stat.Type.TOTEMS_SMALL, Stat.Type.TOTEMS_MEDIUM,
            Stat.Type.TOTEMS_LARGE, Stat.Type.TOTEMS_CROWN
        };
    }

    @Override // from ScenarioInfo
    public int[] getPointsPerObjectives ()
    {
        return new int[] {
            TotemBonus.Type.TOTEM_SMALL.value(),
            TotemBonus.Type.TOTEM_MEDIUM.value(),
            TotemBonus.Type.TOTEM_LARGE.value(),
            TotemBonus.Type.TOTEM_CROWN.value(),
        };
    }

    @Override // from ScenarioInfo
    public String getObjectiveCode ()
    {
        return "totems_stacked";
    }
    
    @Override // from ScenarioInfo
    public Stat.Type getSecondaryObjective ()
    {
        return Stat.Type.TOTEM_POINTS;
    }

    @Override // from ScenarioInfo
    public boolean isValidMarker (Marker marker)
    {
        return super.isValidMarker(marker) || marker.getType() == Marker.TOTEM;
    }

    @Override // from ScenarioInfo
    public StatsView getStatsView (BasicContext ctx)
    {
        return new StatsView(ctx, true);
    }
}
