package fr.neatmonster.nocheatplus.checks.moving.velocity;

/**
 * Flags for use with velocity entries. There may be flags specific to certain
 * types of velocity entries only, these may get prefixed somehow some day.
 * 
 * @author asofold
 *
 */
public class VelocityFlags {

    /** Create new entries with an actActCount increased by one. */
    public static final long SPLIT_RETAIN_ACTCOUNT = 0x0001;

    /**
     * Create a new entry if the used amount leaves at least a third of the set
     * amount. Entries generated by splitting are prepended by default.
     */
    public static final long SPLIT_ABOVE_THIRD = 0x0002;

    /**
     * Create a new entry if the used amount leaves at least an amount of 0.42.
     * Entries generated by splitting are prepended by default.
     */
    public static final long SPLIT_ABOVE_0_42 = 0x0004;

    /**
     * Entries with this flag originate from moving blocks (typically pistons).
     */
    public static final long ORIGIN_BLOCK_MOVE = 0x0100;

    /**
     * Entries with this flag originate from bouncing off blocks (typically
     * slime blocks).
     */
    public static final long ORIGIN_BLOCK_BOUNCE = 0x0200;

    /**
     * Entries with this flag originate from pvp (likely faked, due to the
     * server not sending properly).
     */
    public static final long ORIGIN_PVP = 0x0400;

}