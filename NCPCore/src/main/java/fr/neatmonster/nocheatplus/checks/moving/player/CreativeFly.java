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
package fr.neatmonster.nocheatplus.checks.moving.player;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.magic.LostGround;
import fr.neatmonster.nocheatplus.checks.moving.magic.Magic;
import fr.neatmonster.nocheatplus.checks.moving.model.LiftOffEnvelope;
import fr.neatmonster.nocheatplus.checks.moving.model.ModelFlying;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.moving.util.MovingUtil;
import fr.neatmonster.nocheatplus.checks.moving.velocity.AccountEntry;
import fr.neatmonster.nocheatplus.checks.moving.velocity.SimpleEntry;
import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.compat.BridgeEnchant;
import fr.neatmonster.nocheatplus.compat.BridgeMisc;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker;
import fr.neatmonster.nocheatplus.components.modifier.IAttributeAccess;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.PotionUtil;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.utilities.location.TrigUtil;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;

/**
 * A check designed for people that are allowed to fly. The complement to the "SurvivalFly", which is for people that
 * aren't allowed to fly, and therefore have tighter rules to obey.
 */
public class CreativeFly extends Check {

    private final List<String> tags = new LinkedList<String>();
    private final BlockChangeTracker blockChangeTracker;
    private IGenericInstanceHandle<IAttributeAccess> attributeAccess = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstanceHandle(IAttributeAccess.class);

    /**
     * Instantiates a new creative fly check.
     */
    public CreativeFly() {
        super(CheckType.MOVING_CREATIVEFLY);
        blockChangeTracker = NCPAPIProvider.getNoCheatPlusAPI().getBlockChangeTracker();
    }

    /**
     * 
     * @param player
     * @param from
     * @param to
     * @param data
     * @param cc
     * @param time Milliseconds.
     * @return
     */
    public Location check(final Player player, final PlayerLocation from, final PlayerLocation to, 
            final MovingData data, final MovingConfig cc, final IPlayerData pData,
            final long time, final int tick,
            final boolean useBlockChangeTracker) {

        // Reset tags, just in case.
        tags.clear();

        final boolean debug = pData.isDebugActive(type);

        // Some edge data for this move.
        final GameMode gameMode = player.getGameMode();
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        //        if (!data.thisMove.from.extraPropertiesValid) {
        //            // TODO: Confine by model config flag or just always do [if the latter: do it in the listener]?
        //            data.thisMove.setExtraProperties(from, to);
        //        }
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final ModelFlying model = thisMove.modelFlying;
        // TODO: Other set back policy for elytra, e.g. not set in narrow spaces?

        // Calculate some distances.
        final double yDistance = thisMove.yDistance;
        final double hDistance = thisMove.hDistance;

        final boolean flying = gameMode == BridgeMisc.GAME_MODE_SPECTATOR || player.isFlying();
        final boolean sprinting = time <= data.timeSprinting + cc.sprintingGrace;
        boolean lostground = false;

        // Lost ground, if set so.
        if (model.getGround()) {
            MovingUtil.prepareFullCheck(from, to, thisMove, Math.max(cc.yOnGround, cc.noFallyOnGround));
            if (!thisMove.from.onGroundOrResetCond) {
                if (from.isSamePos(to)) {
                    if (lastMove.toIsValid && lastMove.hDistance > 0.0 && lastMove.yDistance < -0.3 // Copy and paste from sf.
                            && LostGround.lostGroundStill(player, from, to, hDistance, yDistance, sprinting, lastMove, data, cc, tags)) {
                        lostground = true;
                    }
                }
                else if (LostGround.lostGround(player, from, to, hDistance, yDistance, sprinting, lastMove, 
                        data, cc, useBlockChangeTracker ? blockChangeTracker : null, tags)) {
                    lostground = true;
                }
            }
        }

        // Since still check NoFall on CreativeFly, need to exempt who has slow falling effect
        if (Bridge1_13.hasSlowfalling() && model.getScaleSlowfallingEffect()) {
            data.clearNoFallData();
        } else 
        // Accounting for elytra fall damage
        if (Bridge1_9.isGlidingWithElytra(player) && thisMove.yDistance > -0.5) {
            data.clearNoFallData();
        }

        workaroundSwitchingModel(player, thisMove, lastMove, model, data, cc);

        // Horizontal distance check.
        double[] resH = hDist(player, from, to, hDistance, yDistance, sprinting, flying, lastMove, time, model, data, cc);
        double limitH = resH[0];
        double resultH = resH[1];

        double[] rese = hackElytraH(player, from, to, hDistance, yDistance, thisMove, lastMove, lostground, data, cc);
        resultH = Math.max(resultH, rese[1]);
        // Check velocity.
        if (resultH > 0) {
            double hFreedom = data.getHorizontalFreedom();
            if (hFreedom < resultH) {
                // Use queued velocity if possible.
                hFreedom += data.useHorizontalVelocity(resultH - hFreedom);
            }
            if (hFreedom > 0.0) {
                resultH = Math.max(0.0, resultH - hFreedom);
                if (resultH <= 0.0) {
                    limitH = hDistance;
                }
                tags.add("hvel");
            }
        }
        else {
            data.clearActiveHorVel(); // TODO: test/check !
        }

        resultH *= 100.0; // Normalize to % of a block.

        long now = System.currentTimeMillis();
        
        // Sometimes resultH can be up to 18
        if (Bridge1_9.isGliding(player) && (Bridge1_13.isRiptiding(player) || data.timeRiptiding + 4000 > now) && resultH<14.0) {
            resultH = 0.0;
        }

        if (resultH > 0.0) {
            tags.add("hdist");
        }

        // Vertical move.
        double limitV = 0.0; // Limit.
        double resultV = rese[0]; // Violation (normalized to 100 * 1 block, applies if > 0.0).

        // Distinguish checking method by y-direction of the move.
        if (yDistance > 0.0) {
            // Ascend.
            double[] res = vDistAscend(from, to, yDistance, flying, thisMove, lastMove, model, data, cc);
            resultV = Math.max(resultV, res[1]);
            limitV = res[0];
        }
        else if (yDistance < 0.0) {
            // Descend.
            double[] res = vDistDescend(from, to, yDistance, flying, lastMove, model, data, cc);
            resultV = Math.max(resultV, res[1]);
            limitV = res[0];
        }
        else {
            // Keep altitude.
            double[] res = vDistZero(from, to, yDistance, flying, lastMove, model, data, cc);
            resultV = Math.max(resultV, res[1]);
            limitV = res[0];
        }

        // Velocity.
        if (resultV > 0.0 && data.getOrUseVerticalVelocity(yDistance) != null) {
            resultV = 0.0;
            tags.add("vvel");
        }

        // Add tag for maximum height check (silent set back).
        final double maximumHeight = model.getMaxHeight() + player.getWorld().getMaxHeight();
        if (to.getY() > maximumHeight) {
            // TODO: Allow use velocity there (would need a flag to signal the actual check below)?
            tags.add("maxheight");
        }

        resultV *= 100.0; // Normalize to % of a block.
        // Sometimes resultV can be up to 78
        if (Bridge1_9.isGliding(player) && (Bridge1_13.isRiptiding(player) || data.timeRiptiding + 4000 > now) && resultV<60.0) {
            resultV = 0.0;
        }
        if (resultV > 0.0) {
            tags.add("vdist");
        }
        if (lastMove.toIsValid && !player.isFlying() && !Double.isInfinite(Bridge1_9.getLevitationAmplifier(player)) && !Bridge1_9.isGlidingWithElytra(player)
            && thisMove.modelFlying == lastMove.modelFlying
            && !from.isHeadObstructed() && !to.isHeadObstructed()
            //Exempt check for 20 seconds after joined
            && !(now > pData.getLastJoinTime() && pData.getLastJoinTime() + 20000 > now)
            && !from.isInLiquid() && !(thisMove.yDistance < 0.0 && lastMove.yDistance - thisMove.yDistance < 0.0001)) {
                double allowY = (lastMove.yDistance + (0.05D * (Bridge1_9.getLevitationAmplifier(player) + 1) - lastMove.yDistance) * 0.2D) * 0.98;
                if (lastMove.yDistance < 0.0 && thisMove.yDistance < allowY) {
                    resultV = Math.max(resultV,1.0);
                    tags.add("antilevitate");
                } else if (from.getY() >= to.getY() && !(thisMove.yDistance == 0.0 && allowY < 0.0)) {
                    resultV = Math.max(resultV,1.0);
                    tags.add("antilevitate");
                }
        }

        final double result = Math.max(0.0, resultH) + Math.max(0.0, resultV);

        if (debug) {
            outpuDebugMove(player, hDistance, limitH, yDistance, limitV, model, tags, data);
        }

        // Violation handling.
        Location setBack = null; // Might get altered below.
        if (result > 0.0) {
            // Increment violation level.
            data.creativeFlyVL += result;

            // Execute whatever actions are associated with this check and the violation level and find out if we
            // should cancel the event.
            final ViolationData vd = new ViolationData(this, player, data.creativeFlyVL, result, cc.creativeFlyActions);
            if (vd.needsParameters()) {
                vd.setParameter(ParameterName.LOCATION_FROM, String.format(Locale.US, "%.2f, %.2f, %.2f", from.getX(), from.getY(), from.getZ()));
                vd.setParameter(ParameterName.LOCATION_TO, String.format(Locale.US, "%.2f, %.2f, %.2f", to.getX(), to.getY(), to.getZ()));
                vd.setParameter(ParameterName.DISTANCE, String.format(Locale.US, "%.2f", TrigUtil.distance(from,  to)));
                if (!tags.isEmpty()) {
                    vd.setParameter(ParameterName.TAGS, StringUtil.join(tags, "+"));
                }
            }
            if (executeActions(vd).willCancel()) {
                // Compose a new location based on coordinates of "newTo" and viewing direction of "event.getTo()"
                // to allow the player to look somewhere else despite getting pulled back by NoCheatPlus.
                setBack = data.getSetBack(to); // (OK)
            }
        }
        else {
            // Maximum height check (silent set back).
            if (to.getY() > maximumHeight) {
                setBack = data.getSetBack(to); // (OK)
                if (debug) {
                    debug(player, "Maximum height exceeded, silent set-back.");
                }
            }
            if (setBack == null) {
                // Slowly reduce the violation level with each event.
                data.creativeFlyVL *= 0.97;
            }
        }

        // Return setBack, if set.
        if (setBack != null) {
            // Check for max height of the set back.
            if (setBack.getY() > maximumHeight) {
                // Correct the y position.
                setBack.setY(getCorrectedHeight(maximumHeight, setBack.getWorld()));
                if (debug) {
                    debug(player, "Maximum height exceeded by set back, correct to: " + setBack.getY());
                }
            }
            data.sfJumpPhase = 0;
            return setBack;
        }
        else {
            // Adjust the set back and other last distances.
            data.setSetBack(to);
            // Adjust jump phase.
            if (!thisMove.from.onGroundOrResetCond && !thisMove.to.onGroundOrResetCond) {
                data.sfJumpPhase ++;
            }
            else if (thisMove.touchedGround && !thisMove.to.onGroundOrResetCond) {
                data.sfJumpPhase = 1;
            }
            else {
                data.sfJumpPhase = 0;
            }
            return null;
        }
    }

    /**
     * 
     * @param player
     * @param from
     * @param to
     * @param hDistance
     * @param yDistance
     * @param flying
     * @param lastMove
     * @param time
     * @param model
     * @param data
     * @param cc
     * @return limitH, resultH (not normalized).
     */
    private double[] hDist(final Player player, final PlayerLocation from, final PlayerLocation to, final double hDistance, final double yDistance, final boolean sprinting, final boolean flying, final PlayerMoveData lastMove, final long time, final ModelFlying model, final MovingData data, final MovingConfig cc) {
        // Modifiers.
        double fSpeed;

        // TODO: Make this configurable ! [Speed effect should not affect flying if not on ground.]
        if (model.getApplyModifiers()) {
            final double speedModifier = mcAccess.getHandle().getFasterMovementAmplifier(player);
            if (Double.isInfinite(speedModifier)) {
                fSpeed = 1.0;
            }
            else {
                fSpeed = 1.0 + 0.2 * (speedModifier + 1.0);
            }
            if (flying) {
                // TODO: Consider mechanics for flying backwards.
                fSpeed *= data.flySpeed / Magic.DEFAULT_FLYSPEED;
                if (sprinting) {
                    // TODO: Prevent for pre-1.8?
                    fSpeed *= model.getHorizontalModSprint();
                    tags.add("sprint");
                }
                tags.add("flying");
            }
            else {
                // (Ignore sprinting here).
                final double attrMod = attributeAccess.getHandle().getSpeedAttributeMultiplier(player);
                if (attrMod != Double.MAX_VALUE) fSpeed *= attrMod;
                fSpeed *= data.walkSpeed / Magic.DEFAULT_WALKSPEED;
            }
        }
        else {
            fSpeed = 1.0;
        }

        double limitH = model.getHorizontalModSpeed() / 100.0 * ModelFlying.HORIZONTAL_SPEED * fSpeed;

        if (from.isInWater() || to.isInWater()) {
            if (!Double.isInfinite(Bridge1_13.getDolphinGraceAmplifier(player))) {
                limitH *= Magic.modDolphinsGrace;
            }
        }

        if (model.getScaleSlowfallingEffect() && Bridge1_13.hasSlowfalling()) {
            Double Amplifier = PotionUtil.getPotionEffectAmplifier(from.getPlayer(), PotionEffectType.SPEED);
            limitH = Double.isInfinite(Amplifier) ? limitH : limitH + 0.1*(Amplifier +1);
        }

        if (lastMove.toIsValid) {
            // TODO: Use last friction (as well)?
            // TODO: Test/adjust more.
            double frictionDist = lastMove.hDistance * Magic.FRICTION_MEDIUM_AIR;
            limitH = Math.max(frictionDist, limitH);
            tags.add("hfrict");
        }

        // Finally, determine how far the player went beyond the set limits.
        //        double resultH = Math.max(0.0.0, hDistance - data.horizontalFreedom - limitH);
        double resultH = Math.max(0.0, hDistance - limitH);

        if (model.getApplyModifiers()) {
            data.bunnyhopDelay--;
            if (!flying && resultH > 0 && sprinting) {
                // TODO: Flying and bunnyhop ? <- 8 blocks per second - could be a case.
                // Try to treat it as a the "bunnyhop" problem. The bunnyhop problem is that landing and immediately jumping
                // again leads to a player moving almost twice as far in that step.
                // TODO: Real modeling for that kind of moving pattern (same with sf?).
                if (data.bunnyhopDelay <= 0 && resultH < 0.4) {
                    data.bunnyhopDelay = 9;
                    resultH = 0.0;
                    tags.add("bunnyhop");
                }
            }
        }
        return new double[] {limitH, resultH};
    }


    /**
     * 
     * @param from
     * @param to
     * @param yDistance
     * @param flying
     * @param lastMove
     * @param model
     * @param data
     * @param cc
     * @return limitV, resultV (not normalized).
     */
    private double[] vDistAscend(final PlayerLocation from, final PlayerLocation to, final double yDistance, final boolean flying, final PlayerMoveData thisMove, final PlayerMoveData lastMove, final ModelFlying model, final MovingData data, final MovingConfig cc) {
        double limitV = model.getVerticalAscendModSpeed() / 100.0 * ModelFlying.VERTICAL_ASCEND_SPEED; // * data.jumpAmplifier;
        double resultV = 0.0;
        if (model.getApplyModifiers() && flying && yDistance > 0.0) {
            // Let fly speed apply with moving upwards.
            limitV *= data.flySpeed / Magic.DEFAULT_FLYSPEED;
        }
        else if (model.getScaleLevitationEffect() && Bridge1_9.hasLevitation()) {
            // Exclude modifiers for now.
            final double levitation = Bridge1_9.getLevitationAmplifier(from.getPlayer());
            if (levitation > 0.0) {
                // (Double checked.)
                // TODO: Perhaps do with a modifier instead, to avoid confusion.
                limitV += 0.046 * levitation; // (It ends up like 0.5 added extra for some levels of levitation, roughly.)
                tags.add("levitation:" + levitation);
            }
        } else if (model.getScaleSlowfallingEffect() && Bridge1_13.hasSlowfalling()) {
            Double Amplifier = PotionUtil.getPotionEffectAmplifier(from.getPlayer(), PotionEffectType.JUMP);
            limitV += Double.isInfinite(Amplifier) ? 0.5 : 0.5 + 0.1*(Amplifier +1);
        }

        // Related to elytra.
        if (model.getVerticalAscendGliding()) {
            // TODO: Better detection of an elytra model (extra flags?).
            limitV = Math.max(limitV, limitV = hackLytra(yDistance, limitV, thisMove, lastMove, data));
        }
        if (Bridge1_9.isGlidingWithElytra(from.getPlayer()) &&
        (from.isInLiquid() || to.isInLiquid() || data.liqtick > 10)) // recently left water
            limitV = Math.max(limitV, 0.35);

        if (model.getGravity()) {
            // Friction with gravity.
            if (yDistance > limitV && lastMove.toIsValid) { // TODO: gravity/friction?
                // (Disregard gravity.)
                // TODO: Use last friction (as well)?
                double frictionDist = lastMove.yDistance * Magic.FRICTION_MEDIUM_AIR;
                if (!flying) {
                    frictionDist -= 0.019;
                }
                if (frictionDist > limitV) {
                    limitV = frictionDist;
                    tags.add("vfrict_g");
                }
            }
        }

        if (model.getGround()) {
            // Jump lift off gain.
            // NOTE: This assumes SurvivalFly busies about moves with from.onGroundOrResetCond.
            if (yDistance > limitV && !thisMove.to.onGroundOrResetCond && !thisMove.from.onGroundOrResetCond && (
                    // Last move touched ground.
                    lastMove.toIsValid && lastMove.touchedGround && 
                    (lastMove.yDistance <= 0.0 || lastMove.to.extraPropertiesValid && lastMove.to.onGround)
                    // This move touched ground by a workaround.
                    || thisMove.touchedGroundWorkaround
                    )) {
                // Allow normal jumping.
                final double maxGain = LiftOffEnvelope.NORMAL.getMaxJumpGain(data.jumpAmplifier);
                if (maxGain > limitV) {
                    limitV = maxGain;
                    tags.add("jump_gain");
                }
            }
        }

        // Ordinary step up.
        // TODO: Might be within a 'if (model.ground)' block?
        // TODO: sfStepHeight should be a common modeling parameter?
        if (yDistance > limitV && yDistance <= cc.sfStepHeight 
                && (lastMove.toIsValid && lastMove.yDistance < 0.0 || from.isOnGroundOrResetCond() || thisMove.touchedGroundWorkaround)
                && to.isOnGround()) {
            // (Jump effect not checked yet.)
            limitV = cc.sfStepHeight;
            tags.add("step_up");
        }

        // Determine violation amount.
        resultV = Math.max(0.0, yDistance - limitV);

        // Post-violation recovery.


        return new double[] {limitV, resultV};
    }

    /**
     * 
     * @param from
     * @param to
     * @param hDistance
     * @param yDistance
     * @param thisMove
     * @param lastMove
     * @param lostground
     * @param data
     * @param player
     * @return resultH, resultV.
     */
    private double[] hackElytraH(final Player player, final PlayerLocation from, final PlayerLocation to, final double hDistance, final double yDistance, final PlayerMoveData thisMove, 
        final PlayerMoveData lastMove, final boolean lostground, final MovingData data, final MovingConfig cc) {
        /* Known false positives:
         * Still have setback with taking off ?
         * Fall into water
         * Some random falses with quick rotation ?
         */
        if (!cc.elytraStrict || !Bridge1_9.isGlidingWithElytra(player)) return new double[] {0.0, 0.0};
        // Gliding in air
        if (lastMove.toIsValid && lastMove.modelFlying == thisMove.modelFlying && !from.isInLiquid()) {
            final Vector lookvec = to.getLocation().getDirection();
            final float f = to.getPitch() * 0.017453292F;
            final float lastf = lastMove.to.getPitch() * 0.017453292F;
            final float diff = Math.abs(f - lastf);
            double allwyDistance = lastMove.yDistance;
            double allwHDistance = 0.0;
            final double xzlength = Math.sqrt(lookvec.getX() * lookvec.getX() + lookvec.getZ() * lookvec.getZ());
            double f4 = Math.cos(f);
            f4 = f4 * f4;
            allwyDistance += -0.08D + f4 * 0.06D;
            double x = 0.0;
            double z = 0.0;
            if (allwyDistance < 0.0D) {
                final double d = allwyDistance * -0.1 * f4;
                x += lookvec.getX() * d / xzlength;
                z += lookvec.getZ() * d / xzlength;
                allwyDistance += d;
            }

            // Look up
            if (f < 0.0F) {
                final double d = lastMove.hDistance * -Math.sin(f) * 0.04;
                x -= lookvec.getX() * d / xzlength;
                z -= lookvec.getZ() * d / xzlength;
                allwyDistance += d * 3.2;
            }
            x += lookvec.getX() / (xzlength*0.995) * lastMove.hDistance;
            z += lookvec.getZ() / (xzlength*0.995) * lastMove.hDistance;

            // Fireworks
            // Can't be more precise due to some problems, still have ~15% faster bypasses :(
            if (data.fireworksBoostDuration > 0) {
                x += lookvec.getX() * 0.1D + (lookvec.getX() * 1.5D - x) * 0.5D;
                z += lookvec.getZ() * 0.1D + (lookvec.getZ() * 1.5D - z) * 0.5D;
                // Quick rotation problems
                if (Math.abs(f) > Math.abs(lastf)) if (diff > 1.9) allwHDistance += (diff - 0.6)/1.4; else allwHDistance += diff/1.4;
                // Problem with calculating fireworks duration and it end sooner. Speed after boost might be faster a little bit!
                if (f > 0.0 && f < 1.4 && Math.round(data.fireworksBoostTickNeedCheck / 4) > data.fireworksBoostDuration) allwHDistance += 0.24;
                allwHDistance += 0.12;
                // Speed isn't affected by fireworks in first tick
                if (data.fireworksBoostDuration >= data.fireworksBoostTickNeedCheck) allwHDistance += 0.65;
                tags.add("fw_speed");
            }

            //System.out.println(allwyDistance + " " + yDistance + " diff:" + Math.abs(yDistance - allwyDistance * Magic.FRICTION_MEDIUM_AIR) + " diffl:" + diff);
            if (f > -0.11 && Math.abs(yDistance - allwyDistance * Magic.FRICTION_MEDIUM_AIR) > 0.005
            // Ignore fireworks, in water, lostground, slow_falling effect, velocity, honey block
            && data.fireworksBoostDuration <= 0 && data.liqtick == 0 && !lostground && !data.isVelocityJumpPhase()
            && Double.isInfinite(Bridge1_13.getSlowfallingAmplifier(player)) && !isCollideWithHB(from, to)
                ) {
                tags.add("elytra_v_keep");
                return new double[] {yDistance == 0.0 ? 0.1 : Math.abs(yDistance), 0.0};
            }

            // Adjust false
            allwHDistance += Math.sqrt(x*x +z*z) + (f < -1.37 ? 0.15 : 0.0);
            allwyDistance += allwyDistance > 0.0 ? 0.002 : (f < -1.37 ? (f < -1.5 ? -0.394 : -0.18) : -0.04); 
            allwyDistance *= Magic.FRICTION_MEDIUM_AIR;
            
            //System.out.println("f:" + f + " Hdist:" + hDistance + " allow:" + allwHDistance + " yDist:" + yDistance + " allowy:" + allwyDistance + " fwb:" + data.fireworksBoostDuration);
            if (data.fireworksBoostDuration <= 0) {
                if (yDistance > 0.0) {
                    if (allwyDistance < yDistance
                        // Inversion (neg -> pos).
                        && !(yDistance < Magic.GRAVITY_MIN && lastMove.yDistance > -Magic.GRAVITY_MAX - Magic.GRAVITY_MIN && lastMove.yDistance < 0.5 * Magic.GRAVITY_SPAN)) {
                        tags.add("elytra_v_asc");
                        return new double[] {yDistance, 0.0};
                    }
                } else if (allwyDistance > yDistance
                    // Inversion (neg -> pos , pos -> neg).
                    && !(allwyDistance > 0.0 && yDistance > -0.25)) {
                    tags.add("elytra_v_desc");
                    return new double[] {Math.abs(yDistance), 0.0};
                }
            }
            if (allwHDistance < hDistance) {
                tags.add("elytra_h_asc");
                return new double[] {0.0, hDistance - allwHDistance};
            }
        // Gliding in water
        // TODO: Will add later, look unstable
        } //else if(from.isInLiquid() && data.liqtick > 35) {
          //  if (data.timeRiptiding + 4000 > System.currentTimeMillis()) return new double[] {0.0, 0.0};
          //  double allwHDistance = Magic.DEFAULT_WALKSPEED * cc.survivalFlyWalkingSpeed / 100D;
          //  if (data.fireworksBoostDuration > 0) allwHDistance = 1.6;
          //  final int level = BridgeEnchant.getDepthStriderLevel(player);
          //  if (level > 0) {
          //      allwHDistance *= Magic.modDepthStrider[level];
          //  }
          //  if (!Double.isInfinite(Bridge1_13.getDolphinGraceAmplifier(player))) allwHDistance *= 4.9;
          //  if (data.fireworksBoostDuration > 0) allwHDistance = Math.max(allwHDistance, 1.8);
          //  System.out.println(thisMove.hDistance + " " + allwHDistance);
          //  if (thisMove.hDistance > allwHDistance) {
          //      tags.add("elytra_hspeed(water)");
          //      return new double[] {0.0, hDistance - allwHDistance};
          //  }
       // }
        return new double[] {0.0, 0.0};
    }

    private double hackLytra(final double yDistance, final double limitV, final PlayerMoveData thisMove, final PlayerMoveData lastMove, final MovingData data) {
        // TODO: Hack, move / config / something.
        // TODO: Confine more. hdist change relates to ydist change
        // TODO: Further: jumpphase vs. y-distance to set back. Problem: velocity
        // TODO: Further: record max h and descend speeds and relate to those.
        // TODO: Demand total speed to decrease.
        if (yDistance > Magic.GLIDE_DESCEND_PHASE_MIN && yDistance < 17.0 * Magic.GRAVITY_MAX
                && (
                        // Normal envelope.
                        yDistance - lastMove.yDistance < Magic.GRAVITY_MAX * 1.5
                        // Inversion (neg -> pos).
                        || lastMove.yDistance < -Magic.GRAVITY_SPAN && yDistance < Magic.GRAVITY_MAX + Magic.GRAVITY_ODD && yDistance > Magic.GRAVITY_SPAN
                        )
                && thisMove.hDistance < lastMove.hDistance
                && (lastMove.yDistance > 0.0 || lastMove.hDistance > 0.55) // Demand some speed on the transition.
                // Demand total speed to decrease somehow, unless for the very transition.
                && (thisMove.distanceSquared / lastMove.distanceSquared < 0.99
                        || lastMove.yDistance < 0.0) // Might confine the latter something to be tested.
                ) {
            if (lastMove.hDistance > 0.52) {
                // (Increasing y-distance.)
                tags.add("elytra_asc1");
                return yDistance;
            }
            else if (thisMove.hDistance > Magic.GRAVITY_MIN && yDistance < lastMove.yDistance) {
                // (Decreasing y-distance.)
                final PlayerMoveData pastMove1 = data.playerMoves.getSecondPastMove();
                if (pastMove1.toIsValid && pastMove1.to.extraPropertiesValid) {
                    // Demand this being the first one, or decreasing by a decent amount with past two moves.
                    if (
                            // First move rather decreasing.
                            pastMove1.yDistance < lastMove.yDistance 
                            // Decreasing by a reasonable (?) amount.
                            || yDistance - pastMove1.yDistance < -0.001
                            // && yDistance - lastMove.yDistance < lastMove.yDistance - pastMove1.yDistance - 0.0005 // Probably need remove.
                            ) {
                        tags.add("elytra_asc2");
                        return yDistance;
                    }
                }
            }
        }

        // Elytra boost with fireworks rockets.
        if (yDistance > limitV && data.fireworksBoostDuration > 0 && lastMove.toIsValid 
                && (
                        yDistance >= lastMove.yDistance 
                        || yDistance - lastMove.yDistance < Magic.GRAVITY_MAX
                        // TODO: Head blocked -> friction does it?
                        )
                && (
                        yDistance - lastMove.yDistance < 0.77 // TODO
                        || lastMove.yDistance < 0.0 && yDistance < 1.54
                        )
                && yDistance < 1.67 // Last resort, check / TODO
                ) {
            /*
             * TODO: Do cross check item consumption (do other events fire?).
             * [?on tick: expectations framework, check before tick and before
             * other inventory events, once set]
             */
            // TODO: Remove fumbling with magic constants.
            // TODO: Relate horizontal to vertical + relate to looking direction.
            // TODO: More invalidation conditions, like total age (checked elsewhere?).
            tags.add("fw_boost_asc");
            return yDistance;
        }

        return limitV;
    }

    /**
     * 
     * @param from
     * @param to
     * @param yDistance
     * @param flying
     * @param lastMove
     * @param model
     * @param data
     * @param cc
     * @return limitV, resultV
     */
    private double[] vDistDescend(final PlayerLocation from, final PlayerLocation to, final double yDistance, final boolean flying, final PlayerMoveData lastMove, final ModelFlying model, final MovingData data, final MovingConfig cc) {
        double limitV = 0.0;
        double resultV = 0.0;
        // Note that 'extreme moves' are covered by the extreme move check.
        // TODO: if gravity: friction + gravity.
        // TODO: deny falling, possibly special case head-step-down - to be tested (levitation).
        // TODO: min-max envelope (elytra).
        // TODO: ordinary flying (flying: enforce maximum speed at least)
        return new double[] {limitV, resultV};
    }

    /**
     * 
     * @param from
     * @param to
     * @param yDistance
     * @param flying
     * @param lastMove
     * @param model
     * @param data
     * @param cc
     * @return limitV, resultV
     */
    private double[] vDistZero(final PlayerLocation from, final PlayerLocation to, final double yDistance, final boolean flying, final PlayerMoveData lastMove, final ModelFlying model, final MovingData data, final MovingConfig cc) {
        double limitV = 0.0;
        double resultV = 0.0;
        // TODO: Deny on enforcing mingain.
        return new double[] {limitV, resultV};
    }

    private double getCorrectedHeight(final double maximumHeight, final World world) {
        return Math.max(maximumHeight - 10.0, world.getMaxHeight());
    }

    private void workaroundSwitchingModel(final Player player, final PlayerMoveData thisMove, final PlayerMoveData lastMove, final ModelFlying model, final MovingData data, final MovingConfig cc) {
        if (lastMove.toIsValid) {
            // Other modelflying -> levitation
            // TODO: Better horizontal workaround
            if (data.bunnyhopTick > 0) data.bunnyhopTick--;
            if (lastMove.modelFlying != thisMove.modelFlying && thisMove.modelFlying.getScaleLevitationEffect()) {
                data.addVerticalVelocity(new SimpleEntry(0.0, 2));
                final double amount = guessFlyNoFlyVelocity(player, data.playerMoves.getCurrentMove(), lastMove, data);
                data.addHorizontalVelocity(new AccountEntry(amount, 2, MovingData.getHorVelValCount(amount)));
                data.bunnyhopTick = 20;
                if (thisMove.yDistance > 0.0) {
                    data.yDis = thisMove.yDistance;
                    data.addVerticalVelocity(new SimpleEntry(data.yDis, 1));
                } else {
                    data.bunnyhopTick = 21;
                    final double jumpamplifier = PotionUtil.getPotionEffectAmplifier(player, PotionEffectType.JUMP);
                    data.yDis = 0.42 * (1 + 0.24*(Double.isInfinite(jumpamplifier) ? 0.0 : jumpamplifier + 1));
                    data.addVerticalVelocity(new SimpleEntry(data.yDis, 2));
                }
                return;
            } else if (thisMove.modelFlying.getScaleLevitationEffect() && data.bunnyhopTick > 0 && thisMove.yDistance > 0.0 && thisMove.yDistance < data.yDis * 0.95) {
                if (data.bunnyhopTick == 20) return;
                data.yDis = (data.yDis + (0.05D * (Bridge1_9.getLevitationAmplifier(player) + 1) - data.yDis) * 0.2D) * Magic.FRICTION_MEDIUM_AIR;
                data.addVerticalVelocity(new SimpleEntry(thisMove.yDistance, 1));
                final double levitation = Bridge1_9.getLevitationAmplifier(player);
                if (thisMove.yDistance < 0.1 + 0.046 * (levitation > 0.0 ? levitation : 0.0)) data.bunnyhopTick = 0;
                return;
            } else if (thisMove.yDistance < 0.0) data.bunnyhopTick = 0;

            //Gliding -> Creative
            if (lastMove.modelFlying != null && lastMove.modelFlying.getVerticalAscendGliding() && thisMove.modelFlying.getId().equals("gamemode.creative")) {
                final double amount = guessFlyNoFlyVelocity(player, data.playerMoves.getCurrentMove(), lastMove, data);
                data.addHorizontalVelocity(new AccountEntry(amount, 2, MovingData.getHorVelValCount(amount)));
                data.addVerticalVelocity(new SimpleEntry(0.0, 2));
                return;
            }
            // TODO: Levitation -> Slow_falling
        }
    }
    private static double guessFlyNoFlyVelocity(final Player player, 
            final PlayerMoveData thisMove, final PlayerMoveData lastMove, final MovingData data) {
        // Default margin: Allow slightly less than the previous speed.
        final double defaultAmount = lastMove.hDistance * (1.0 + Magic.FRICTION_MEDIUM_AIR) / 2.0;
        // Test for exceptions.
        if (thisMove.hDistance > defaultAmount && Bridge1_9.isWearingElytra(player) && lastMove.modelFlying != null && lastMove.modelFlying.getId().equals(MovingConfig.ID_JETPACK_ELYTRA)) {
            // Allowing the same speed won't always work on elytra (still increasing, differing modeling on client side with motXYZ).
            // (Doesn't seem to be overly effective.)
            final PlayerMoveData secondPastMove = data.playerMoves.getSecondPastMove();
            if (secondPastMove.modelFlying != null && Magic.glideEnvelopeWithHorizontalGain(thisMove, lastMove, secondPastMove)) {
                return thisMove.hDistance + Magic.GLIDE_HORIZONTAL_GAIN_MAX;
            }
        }
        return defaultAmount;
    }

    private boolean isCollideWithHB(PlayerLocation from ,PlayerLocation to) {
        final boolean a = BlockProperties.collides(to.getBlockCache(), to.getMinX() - 0.1, to.getMinY(), to.getMinZ() - 0.1, to.getMaxX() + 0.1, to.getMaxY(), to.getMaxZ() + 0.1, BlockProperties.F_STICKY) ||
        BlockProperties.collides(from.getBlockCache(), from.getMinX() - 0.1, from.getMinY(), from.getMinZ() - 0.1, from.getMaxX() + 0.1, from.getMaxY(), from.getMaxZ() + 0.1, BlockProperties.F_STICKY);
        return a;
    }

    private void outpuDebugMove(final Player player, final double hDistance, final double limitH, 
            final double yDistance, final double limitV, final ModelFlying model, final List<String> tags, 
            final MovingData data) {
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        StringBuilder builder = new StringBuilder(350);
        final String dHDist = lastMove.toIsValid ? " (" + StringUtil.formatDiff(hDistance, lastMove.hDistance) + ")" : "";
        final String dYDist = lastMove.toIsValid ? " (" + StringUtil.formatDiff(yDistance, lastMove.yDistance)+ ")" : "";
        builder.append("hDist: " + hDistance + dHDist + " / " + limitH + " , vDist: " + yDistance + dYDist + " / " + limitV);
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        if (lastMove.toIsValid) {
            builder.append(" , fdsq: " + StringUtil.fdec3.format(thisMove.distanceSquared / lastMove.distanceSquared));
        }
        if (thisMove.verVelUsed != null) {
            builder.append(" , vVelUsed: " + thisMove.verVelUsed);
        }
        if (data.fireworksBoostDuration > 0 && MovingConfig.ID_JETPACK_ELYTRA.equals(model.getId())) {
            builder.append(" , boost: " + data.fireworksBoostDuration);
        }
        builder.append(" , model: " + model.getId());
        if (!tags.isEmpty()) {
            builder.append(" , tags: ");
            builder.append(StringUtil.join(tags, "+"));
        }
        builder.append(" , jumpphase: " + data.sfJumpPhase);
        thisMove.addExtraProperties(builder, " , ");
        debug(player, builder.toString());
    }

}
