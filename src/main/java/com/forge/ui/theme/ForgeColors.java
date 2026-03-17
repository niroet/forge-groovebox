package com.forge.ui.theme;

import javafx.scene.paint.Color;

/**
 * Central color palette for the FORGE DOOM theme.
 * Argent energy reds/oranges, Vega blues, void blacks.
 */
public final class ForgeColors {

    private ForgeColors() {}

    // Argent energy — reds, oranges, ambers
    public static final Color ARGENT_RED    = Color.web("#ff2200");
    public static final Color ARGENT_ORANGE = Color.web("#ff6600");
    public static final Color ARGENT_AMBER  = Color.web("#ff8800");
    public static final Color ARGENT_YELLOW = Color.web("#ffcc00");

    // Vega system — blues/cyans
    public static final Color VEGA_BLUE     = Color.web("#44bbff");
    public static final Color VEGA_CYAN     = Color.web("#88ccee");

    // Divine gold accent
    public static final Color DIVINE_GOLD   = Color.web("#ffdd44");

    // Backgrounds — void/panel/inset
    public static final Color BG_VOID       = Color.web("#080808");
    public static final Color BG_PANEL      = Color.web("#0a0a0a");
    public static final Color BG_INSET      = Color.web("#050505");

    // Borders
    public static final Color BORDER_DIM    = Color.web("#222222");
    public static final Color BORDER_ACTIVE = Color.web("#333333");

    // Text
    public static final Color TEXT_DIM      = Color.web("#666666");
    public static final Color TEXT_LABEL    = Color.web("#888888");

    /**
     * Convert a Color to its hex string representation (e.g. "#ff6600").
     */
    public static String hex(Color c) {
        return String.format("#%02x%02x%02x",
            (int)(c.getRed()   * 255),
            (int)(c.getGreen() * 255),
            (int)(c.getBlue()  * 255));
    }
}
