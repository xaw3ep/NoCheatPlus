/*
 * This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.neatmonster.nocheatplus.checks.fight;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.model.LiftOffEnvelope;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveInfo;
import fr.neatmonster.nocheatplus.checks.moving.util.AuxMoving;
import fr.neatmonster.nocheatplus.checks.moving.util.MovingUtil;
import fr.neatmonster.nocheatplus.penalties.IPenaltyList;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;

/**
 * A check used to verify that critical hits done by players are legit.
 */
public class Critical extends Check {

    private final AuxMoving auxMoving = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstance(AuxMoving.class);

    /**
     * Instantiates a new critical check.
     */
    public Critical() {
        super(CheckType.FIGHT_CRITICAL);
    }

    /**
     * Checks a player.
     * 
     * @param player
     *            the player
     * @return true, if successful
     */
    public boolean check(final Player player, final Location loc, 
            final FightData data, final FightConfig cc, 
            final IPlayerData pData, final IPenaltyList penaltyList) {
        boolean cancel = false;

        final double mcFallDistance = (double) player.getFallDistance();

        // Check if the hit was a critical hit (very small fall-distance, not on ladder, 
        //  not in liquid, not in vehicle, and without blindness effect).
        if (mcFallDistance > 0.0 && !player.isInsideVehicle() 
                && !player.hasPotionEffect(PotionEffectType.BLINDNESS)) {

            if (pData.isDebugActive(type)) {
                debug(player, "y=" + loc.getY() + " mcfalldist=" + mcFallDistance);
            }

            // Might be a violation.
            final MovingConfig mcc = pData.getGenericInstance(MovingConfig.class);
            final MovingData dataM = pData.getGenericInstance(MovingData.class);
            /*
             * TODO: NoFall data max y. (past moves too perhaps - low jump,
             * number split moves without reason)
             */

            // TODO: Skip near the highest jump height (needs check if head collided with something solid, which also detects low jump).
            if (!dataM.isVelocityJumpPhase() && 
                    (dataM.sfLowJump && !dataM.sfNoLowJump && dataM.liftOffEnvelope == LiftOffEnvelope.NORMAL
                    || mcFallDistance < cc.criticalFallDistance && !BlockProperties.isResetCond(player, loc, mcc.yOnGround))) {
                // TODO: Use past move tracking to check for SurvivalFly and the like?
                final PlayerMoveInfo moveInfo = auxMoving.usePlayerMoveInfo();
                moveInfo.set(player, loc, null, mcc.yOnGround);
                if (MovingUtil.shouldCheckSurvivalFly(player, moveInfo.from, dataM, mcc, pData)) {
                    data.criticalVL += 1.0;
                    // Execute whatever actions are associated with this check and 
                    //  the violation level and find out if we should cancel the event.
                    final ViolationData vd = new ViolationData(this, player, data.criticalVL, 1.0, cc.criticalActions);
                    if (vd.needsParameters()) {
                        final List<String> tags = new ArrayList<String>();
                        if (dataM.sfLowJump) {
                            tags.add("lowjump");
                        }
                        vd.setParameter(ParameterName.TAGS, StringUtil.join(tags, "+"));
                    }
                    cancel = executeActions(vd).willCancel();
                    // TODO: Introduce penalty instead of cancel.
                    
                }
                auxMoving.returnPlayerMoveInfo(moveInfo);
            } else data.criticalVL *= 0.93;
        }

        return cancel;
    }

}
