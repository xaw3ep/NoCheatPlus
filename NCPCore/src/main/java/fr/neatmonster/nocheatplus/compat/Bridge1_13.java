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
package fr.neatmonster.nocheatplus.compat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import fr.neatmonster.nocheatplus.utilities.PotionUtil;
import fr.neatmonster.nocheatplus.utilities.ReflectionUtil;

public class Bridge1_13 {
	private static final PotionEffectType SLOWFALLING = PotionEffectType.getByName("SLOW_FALLING");
	private static final PotionEffectType DOLPHINSGRACE = PotionEffectType.getByName("DOLPHINS_GRACE");
	private static final boolean hasIsRiptiding = ReflectionUtil.getMethodNoArgs(Player.class, "isRiptiding", boolean.class) != null;
	private static final boolean hasIsSwimming = ReflectionUtil.getMethodNoArgs(Player.class, "isSwimming", boolean.class) != null;
	
	public static boolean hasSlowfalling() {
        return SLOWFALLING != null;
    }
	
	public static boolean hasDolphinGrace() {
        return DOLPHINSGRACE != null;
    }
	
	public static boolean hasIsRiptiding() {
        return hasIsRiptiding;
    }
	
	public static boolean hasIsSwimming() {
        return hasIsSwimming;
    }
	
	/**
     * Test for the 'slowfalling' potion effect.
     * 
     * @param player
     * @return Double.NEGATIVE_INFINITY if not present.
     */
	public static double getSlowfallingAmplifier(final Player player) {
        if (SLOWFALLING == null) {
            return Double.NEGATIVE_INFINITY;
        }
        return PotionUtil.getPotionEffectAmplifier(player, SLOWFALLING);
    }
	
	/**
     * Test for the 'slowfalling' potion effect.
     * 
     * @param LivingEntity
     * @return Double.NEGATIVE_INFINITY if not present.
     */
	public static double getSlowfallingAmplifier(final LivingEntity entity) {
        if (SLOWFALLING == null) {
            return Double.NEGATIVE_INFINITY;
        }
        return PotionUtil.getPotionEffectAmplifier(entity, SLOWFALLING);
    }
	
	public static double getDolphinGraceAmplifier(final Player player) {
        if (DOLPHINSGRACE == null) {
            return Double.NEGATIVE_INFINITY;
        }
        return PotionUtil.getPotionEffectAmplifier(player, DOLPHINSGRACE);
    }
	
	public static boolean isRiptiding(final Player player) {
        return hasIsRiptiding ? player.isRiptiding() : false;
    }
	
	public static boolean isSwimming(final Player player) {
        return hasIsSwimming ? player.isSwimming() : false;
    }
}
