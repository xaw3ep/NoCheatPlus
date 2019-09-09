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
package fr.neatmonster.nocheatplus.compat.bukkit.model;

import java.text.DecimalFormat;

import org.bukkit.World;
import org.bukkit.block.Block;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

public class BukkitBamboo implements BukkitShapeModel {
	private DecimalFormat df = new DecimalFormat("#.000");
	private final double xz = 0.375;

    @Override
    public double[] getShape(final BlockCache blockCache, 
            final World world, final int x, final int y, final int z) {

        final Block block = world.getBlockAt(x, y, z);
        
        double minX = Math.abs(block.getBoundingBox().getMinX()) - Math.abs((int) block.getBoundingBox().getMinX());
        double minZ = Math.abs(block.getBoundingBox().getMinZ()) - Math.abs((int) block.getBoundingBox().getMinZ());
        
        minX = Double.parseDouble(df.format(minX));
        minZ = Double.parseDouble(df.format(minZ));

        //return new double[] {1.0 - minX + 0.09, 0.0, minZ + 0.09, 1.0 - minX + xz - 0.09, 1.0, minZ + xz - 0.09}; (1)
        //return new double[] {minX + 0.09, 0.0, 1.0 - minZ + 0.09, minX + xz - 0.09, 1.0, 1.0 - minZ + xz - 0.09}; (2)
        // (1) + (2) =>
        return new double[] {1.0 - minX + 0.09, 0.0, 1.0 - minZ + 0.09, 1.0 - minX + xz - 0.09, 1.0, 1.0 - minZ + xz - 0.09}; 
    }

    @Override
    public int getFakeData(final BlockCache blockCache, 
            final World world, final int x, final int y, final int z) {
        return 0;
    }

}
