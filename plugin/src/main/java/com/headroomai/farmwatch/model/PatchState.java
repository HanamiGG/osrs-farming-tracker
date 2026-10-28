package com.headroomai.farmwatch.model;

import java.time.Instant;
import lombok.Data;

@Data
public class PatchState
{
    /** Stable id, e.g. "ARDOUGNE_HERB". Matches FarmingPatch enum name when wired. */
    private String id;

    /** PatchImplementation enum name: HERB, ALLOTMENT, TREE, FRUIT_TREE, etc. */
    private String implementation;

    /** Region label: ARDOUGNE, CATHERBY, FALADOR, ... */
    private String region;

    /** Produce enum name: RANARR, SNAPDRAGON, MAGIC_TREE, ... */
    private String produce;

    /** CropState enum name: GROWING, HARVESTABLE, DISEASED, DEAD, EMPTY, UNCHECKED. */
    private String state;

    /** When the current cycle began (approx). */
    private Instant cycleStartedAt;

    /** When the patch will be ready (if currently growing). */
    private Instant readyAt;

    /** NONE, COMPOST, SUPERCOMPOST, ULTRACOMPOST. */
    private String compost;

    /** Protection payment made. */
    private boolean protectedPaid;

    private boolean diseased;
    private boolean dead;

    /** Raw varbit value as read from stock tracker config. Decoded on the web side via a mapping table. */
    private int varbitValue = -1;

    /** regionID.varbitID numeric pair, kept so the web decoder can look up patch metadata. */
    private int regionId = -1;
    private int varbitId = -1;

    private Instant lastUpdated;
}
