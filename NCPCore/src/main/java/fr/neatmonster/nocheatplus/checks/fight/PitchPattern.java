package fr.neatmonster.nocheatplus.checks.fight;

import java.util.ArrayList;
import java.util.HashMap;
//import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.players.IPlayerData;

public class PitchPattern extends Check {
	
	public PitchPattern() {
		super(CheckType.FIGHT_PITCHPATTERN);
	}
  //private final List<String> tags = new LinkedList<String>();
	
  /*
  *	This check attempts to find the GCD of the players pitch movements.
  *	All mouse movements should be divisible by a constant defined by your
  *	mouse sensivity. Therefore, if it changes drastically or has a very low
  *	GCD (since the constant is being changed), the player is likely
  *	cheating. This can also detect cheats that change the players pitch
  *	as well. Such as scaffold, aimbot, legit/ghost aura, derp, etc.
  *
  */
  public void check(final Player player, final float deltaPitch, final FightConfig cc, final IPlayerData pData, final FightData data) {
    UUID uuid = player.getUniqueId();
    List<Float> lastDeltaPitches = data.deltaPitches.getOrDefault(uuid, new ArrayList());

        // Don't include it in the sample if their pitch didn't change or
        // their pitch is above the limit.
        if(deltaPitch != 0 && Math.abs(deltaPitch) <= cc.pitchPatternLimit) {
            lastDeltaPitches.add(Math.abs(deltaPitch));
        }

        // Samples of the pitch changes are at or bigger than the sample
        if(lastDeltaPitches.size() >= cc.pitchPatternSample) {
            float deltaPitchGCD = roundedGCD(lastDeltaPitches);
            float lastDeltaPitchGCD = data.lastDeltaPitchGCDs.getOrDefault(uuid, deltaPitchGCD);
            float gcdDiff = Math.abs(deltaPitchGCD - lastDeltaPitchGCD);

            // Pitch GCD is a lot different from last time or the GCD is just too small
            if((gcdDiff > cc.pitchPatternDiff || deltaPitchGCD < cc.pitchPatternGCD) & !data.loginExempt) {
                executeActions(player, data.pitchPatternVL, 1D, pData.getGenericInstance(FightConfig.class).pitchPatternActions).willCancel();
                data.pitchPatternVL += 1;
            }
            else {
            data.pitchPatternVL *= 0.98;
            data.loginExempt = false;
            }

            lastDeltaPitches.clear();
            //tags.clear();
            data.lastDeltaPitchGCDs.put(uuid, deltaPitchGCD);
        }

        data.deltaPitches.put(uuid, lastDeltaPitches);
	}
	
  public static float roundedGCD(float x, float y) {
      if(x == 0) {
        return y;
      }
        
      int quotient = getQuotient(y, x);
      float remainder = ((y / x) - quotient) * x;
      if(Math.abs(remainder) < Math.max(x, y) * 1E-3F) {
      remainder = 0;	
      }
      return roundedGCD(remainder, x);
    }

    public static float roundedGCD(List<Float> values) {
        float answer = values.get(0);
        for (int i = 1; i < values.size(); i++) {
            answer = roundedGCD(values.get(i), answer);
        }
        return answer;
    }

    public static int getQuotient(float dividend, float divisor) {
        float answer = dividend / divisor;
        float error = Math.max(dividend, divisor) * 1E-3F;
        return (int)(answer + error);
    }
		

}
