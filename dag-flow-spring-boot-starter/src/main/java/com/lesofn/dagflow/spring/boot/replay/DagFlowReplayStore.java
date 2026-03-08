package com.lesofn.dagflow.spring.boot.replay;

import com.lesofn.dagflow.replay.DagFlowReplay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LRU cache that stores the most recent N {@link DagFlowReplay} execution records.
 * Thread-safe for concurrent reads and writes.
 *
 * @author sofn
 */
public class DagFlowReplayStore {

    private final LinkedHashMap<String, DagFlowReplay> cache;

    public DagFlowReplayStore(int maxSize) {
        this.cache = new LinkedHashMap<>(maxSize + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, DagFlowReplay> eldest) {
                return size() > maxSize;
            }
        };
    }

    /**
     * Store a replay record.
     */
    public synchronized void put(DagFlowReplay replay) {
        if (replay != null) {
            cache.put(replay.getId(), replay);
        }
    }

    /**
     * Get a replay record by ID.
     */
    public synchronized DagFlowReplay get(String id) {
        return cache.get(id);
    }

    /**
     * Get all stored records (most recent last).
     */
    public synchronized List<DagFlowReplay> getAll() {
        return new ArrayList<>(cache.values());
    }

    /**
     * Get the number of stored records.
     */
    public synchronized int size() {
        return cache.size();
    }

    /**
     * Clear all stored records.
     */
    public synchronized void clear() {
        cache.clear();
    }
}
