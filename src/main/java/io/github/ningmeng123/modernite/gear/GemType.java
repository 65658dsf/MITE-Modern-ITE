package io.github.ningmeng123.modernite.gear;

/** Source gem categories and per-rank numeric values. @author NingMeng123 */
public enum GemType {
    DAMAGE("damage", 1), HEALTH("health", 1), PROTECTION("protection", .25),
    RECOVER("recover", .125), EFFICIENCY("effect", 1), DURABILITY("durable", 1),
    SPEED("speed", 1), FIREPROOF("fireproof", 1), EXPLOSION_PROOF("explosionproof", 1),
    NUTRITION("nutrition", 1);
    public final String id;
    public final double rate;
    GemType(String id, double rate) { this.id = id; this.rate = rate; }
}
