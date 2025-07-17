package GaT.search;

import GaT.game.TTEntry;
import java.util.HashMap;
import java.util.Map;

/**
 * SIMPLE TRANSPOSITION TABLE for Engine.java
 *
 * Clean, fast hash table without complex dependencies:
 * ✅ Basic put/get operations
 * ✅ Simple eviction when full
 * ✅ Replacement strategy (depth-preferred)
 * ✅ Essential statistics only
 * ✅ Works with existing TTEntry class
 * ✅ No SearchConfig dependencies
 *
 * Designed specifically for the new Engine architecture.
 */
public class SimpleTranspositionTable {

    // === SIMPLE CONFIGURATION ===
    private static final int DEFAULT_SIZE = 1_000_000;  // 1M entries
    private static final double EVICTION_THRESHOLD = 0.8; // Evict when 80% full

    // === CORE TABLE ===
    private final Map<Long, TTEntry> table;
    private final int maxSize;

    // === SIMPLE STATISTICS ===
    private int hits = 0;
    private int misses = 0;
    private int stores = 0;
    private int evictions = 0;

    public SimpleTranspositionTable() {
        this(DEFAULT_SIZE);
    }

    public SimpleTranspositionTable(int maxSize) {
        this.maxSize = maxSize;
        this.table = new HashMap<>(maxSize / 4); // Start smaller, let HashMap grow
    }

    /**
     * Get entry from table
     */
    public TTEntry get(long hash) {
        TTEntry entry = table.get(hash);

        if (entry != null) {
            hits++;
            return entry;
        } else {
            misses++;
            return null;
        }
    }

    /**
     * Store entry in table
     */
    public void put(long hash, TTEntry entry) {
        if (entry == null) return;

        stores++;

        // Check if we need to evict entries
        if (table.size() >= maxSize * EVICTION_THRESHOLD) {
            evictEntries();
        }

        // Check if we should replace existing entry
        TTEntry existing = table.get(hash);
        if (existing != null && !shouldReplace(existing, entry)) {
            return; // Don't replace better entry
        }

        table.put(hash, entry);
    }

    /**
     * Simple eviction - clear half the table
     */
    private void evictEntries() {
        int targetSize = maxSize / 2;

        if (table.size() <= targetSize) return;

        // Simple strategy: clear entire table (fastest)
        // In practice, this is often more efficient than selective eviction
        table.clear();
        evictions++;

        System.out.printf("🔧 TT eviction: cleared table (was %d entries)%n", table.size());
    }

    /**
     * Decide whether to replace an existing entry
     */
    private boolean shouldReplace(TTEntry existing, TTEntry newEntry) {
        // Always replace if new entry has higher depth
        if (newEntry.depth > existing.depth) {
            return true;
        }

        // Don't replace if existing has much higher depth
        if (existing.depth > newEntry.depth + 2) {
            return false;
        }

        // For same depth, prefer exact scores over bounds
        if (newEntry.depth == existing.depth) {
            if (newEntry.flag == TTEntry.EXACT && existing.flag != TTEntry.EXACT) {
                return true;
            }
            if (existing.flag == TTEntry.EXACT && newEntry.flag != TTEntry.EXACT) {
                return false;
            }
        }

        // Default: replace (newer is often better)
        return true;
    }

    /**
     * Check if entry is usable for alpha-beta cutoff
     */
    public boolean isUsable(TTEntry entry, int depth, int alpha, int beta) {
        if (entry == null || entry.depth < depth) {
            return false;
        }

        switch (entry.flag) {
            case TTEntry.EXACT:
                return true;
            case TTEntry.LOWER_BOUND:
                return entry.score >= beta;
            case TTEntry.UPPER_BOUND:
                return entry.score <= alpha;
            default:
                return false;
        }
    }

    /**
     * Clear the table
     */
    public void clear() {
        table.clear();
        hits = 0;
        misses = 0;
        stores = 0;
        evictions = 0;
    }

    /**
     * Get current table size
     */
    public int size() {
        return table.size();
    }

    /**
     * Get maximum table size
     */
    public int maxSize() {
        return maxSize;
    }

    /**
     * Check if table is nearly full
     */
    public boolean isNearlyFull() {
        return table.size() >= maxSize * EVICTION_THRESHOLD;
    }

    /**
     * Get hit rate as percentage
     */
    public double getHitRate() {
        int total = hits + misses;
        return total > 0 ? (100.0 * hits / total) : 0.0;
    }

    /**
     * Get simple statistics
     */
    public String getStatistics() {
        int total = hits + misses;
        double hitRate = total > 0 ? (100.0 * hits / total) : 0.0;
        double loadFactor = (100.0 * table.size() / maxSize);

        return String.format("TT: %d/%d entries (%.1f%% full), %.1f%% hit rate, %d stores, %d evictions",
                table.size(), maxSize, loadFactor, hitRate, stores, evictions);
    }

    /**
     * Get basic performance info
     */
    public String getPerformanceInfo() {
        return String.format("TT Performance: %d hits, %d misses, %d stores", hits, misses, stores);
    }

    /**
     * Check if table needs maintenance
     */
    public boolean needsMaintenance() {
        return table.size() >= maxSize * 0.9; // 90% full
    }

    /**
     * Get memory usage estimate (rough)
     */
    public double getMemoryUsageMB() {
        // Rough estimate: each entry ~50 bytes (hash + TTEntry + HashMap overhead)
        return table.size() * 50.0 / (1024 * 1024);
    }

    /**
     * Reset statistics (keep table contents)
     */
    public void resetStatistics() {
        hits = 0;
        misses = 0;
        stores = 0;
        evictions = 0;
    }
}