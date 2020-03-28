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
package fr.neatmonster.nocheatplus.checks.net.protocollib;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.bukkit.plugin.Plugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketEvent;

import fr.neatmonster.nocheatplus.checks.fight.FightData;
import fr.neatmonster.nocheatplus.players.DataManager;

public class Fight extends BaseAdapter{
    private static PacketType[] initPacketTypes() {
        final List<PacketType> types = new LinkedList<PacketType>(Arrays.asList(
                PacketType.Play.Client.ARM_ANIMATION
                ));
        return types.toArray(new PacketType[types.size()]);
    }

    public Fight(Plugin plugin) {
        super(plugin, ListenerPriority.LOW, initPacketTypes());
    }

    @Override
    public void onPacketReceiving(final PacketEvent event) {
        handleAnmationPacket(event);
    }

    public void handleAnmationPacket(final PacketEvent event) {
        if (event.isPlayerTemporary()) return;
        final FightData data = DataManager.getGenericInstance(event.getPlayer(), FightData.class);
        data.noSwingPacket = true;
        data.noSwingArmSwung = true;
    }
}
