package fr.neatmonster.nocheatplus.checks.inventory;

import java.util.LinkedList;
import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.entity.Player;


import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.StringUtil;

/**
 * A simple check to prevent players from interacting with their inventory if they shouldn't be allowed to.
 */

public class InventoryMove extends Check {
	
	private final List<String> tags = new LinkedList<String>();
	
	public InventoryMove() {
        super(CheckType.INVENTORY_MOVE);
    }
	
    public boolean check(final Player player, final InventoryData data, final IPlayerData pData, final InventoryConfig cc, final SlotType type) {
		
    boolean cancel = false;
    
    // Tags 
    if (player.isBlocking()) {
    	tags.add("isBlocking");
    }
    else if (player.isSneaking()) {
    	tags.add("isSneaking");
    }
    else if (player.isSwimming()) {
    	tags.add("isSwimming");
    }
    else if (player.isSprinting()) {
    	tags.add("isSprinting");
    }
    else if (player.isDead()) {
    	tags.add("isDead");
    }
    
     // Do the actual check 
     if (player.isSneaking() || player.isSprinting() || player.isSwimming() || player.isBlocking() || player.isDead()) {
           // Check if the player is in creative
   	   if (player.getGameMode() == GameMode.CREATIVE) {
   		   // (Skip hotbar interactions)
   		   if (type == SlotType.QUICKBAR) {
			// Prevent tag addition/spam
			tags.clear();
   		    	return false; 
   		   }
   	           // Check if the Disable_Creative option is not enabled
   		   else if (!cc.invMoveDisableCreative) {
   		       // Violation
    	                data.invMoveVL += 1.0D;
	                final ViolationData vd = new ViolationData(this, player, data.invMoveVL, 1, pData.getGenericInstance(InventoryConfig.class).invMoveActionList);
	                if (vd.needsParameters()){
	                  vd.setParameter(ParameterName.TAGS, StringUtil.join(tags, "+"));
	                }
	             // Clear tags
	             tags.clear();
	             return executeActions(vd).willCancel();
                   }
   		   // Disable_Creative is enabled, skip
   		   else {
		     // Prevent tag addition/spam
	             tags.clear();
   	             return false;
   		   }
   	   }
   	   // Player is not in creative
   	   else {
   	      // Violation
	      data.invMoveVL += 1.0D;
              final ViolationData vd = new ViolationData(this, player, data.invMoveVL, 1, pData.getGenericInstance(InventoryConfig.class).invMoveActionList);
              if (vd.needsParameters()){
                  vd.setParameter(ParameterName.TAGS, StringUtil.join(tags, "+"));
              }
           // Clear tags
           tags.clear();
           return executeActions(vd).willCancel();
        }  
      }
      return cancel;
   }
 }
