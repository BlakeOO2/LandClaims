// ClaimFlag.java
package org.example;

public enum ClaimFlag {
    // Global flags
    PVP(false),
    CREEPER_DAMAGE(false),
    EXPLOSIONS(false),
    FIRE_SPREAD(false),
    MOB_GRIEFING(false),
    REDSTONE(true),
    PISTONS(true),
    HOPPERS(true),
    MONSTERS(false),      // Added back
    LEAF_DECAY(true),    // Added back
    SUPPRESS_SIGN_MESSAGES(false),
    WILD_BUILD(true),        // New flag for building in unclaimed areas (wild)
    WILD_INTERACT(true),     // New flag for interacting in unclaimed areas


    // Trusted player flags
    TRUSTED_CONTAINERS(true),
    TRUSTED_DOORS(true),
    TRUSTED_BUILD(true),
    TRUSTED_VILLAGER_TRADING(true),  // Added

    // Untrusted player flags
    UNTRUSTED_CONTAINERS(false),
    UNTRUSTED_DOORS(false),
    UNTRUSTED_BUILD(false),
    UNTRUSTED_VILLAGER_TRADING(false);  // Added

    private final boolean defaultValue;

    ClaimFlag(boolean defaultValue) {
        this.defaultValue = defaultValue;
    }

    public boolean getDefaultValue() {
        return defaultValue;
    }
}
