package GaT.search;

import GaT.model.TTEntry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TranspositionTable {

    private final Map<Long, TTEntry> table;
    private final int maxSize;
    private long accessCounter = 0;

    public TranspositionTable(int maxSize) {
        this.maxSize = maxSize;
        this.table = new HashMap<>(maxSize * 4 / 3); // Avoid rehashing
    }

    public TTEntry get(long hash) {
        TTEntry entry = table.get(hash);
        if (entry != null) {
            entry.lastAccessed = ++accessCounter;
        }
        return entry;
    }

    public void put(long hash, TTEntry entry) {
        // Check if we need to evict entries
        if (table.size() >= maxSize) {
            evictOldEntries();
        }

        entry.lastAccessed = ++accessCounter;
        table.put(hash, entry);
    }

    private void evictOldEntries() {
        // Strategy: Remove 25% of oldest entries
        int toRemove = maxSize / 4;

        // Find entries to remove (oldest by access time)
        List<Long> toEvict = table.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e1.getValue().lastAccessed, e2.getValue().lastAccessed))
                .limit(toRemove)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Remove them
        toEvict.forEach(table::remove);

        System.out.println("TT: Evicted " + toEvict.size() + " entries, size now: " + table.size());
    }

    public void clear() {
        table.clear();
        accessCounter = 0;
    }

    public int size() {
        return table.size();
    }

    public double getHitRate() {
        // Could be tracked for statistics
        return 0.0; // Placeholder
    }
}
