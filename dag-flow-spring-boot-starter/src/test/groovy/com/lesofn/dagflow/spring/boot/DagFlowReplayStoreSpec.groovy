package com.lesofn.dagflow.spring.boot

import com.lesofn.dagflow.replay.DagFlowReplay
import com.lesofn.dagflow.replay.NodeReplayRecord
import com.lesofn.dagflow.spring.boot.replay.DagFlowReplayStore
import spock.lang.Specification

import java.time.Instant

class DagFlowReplayStoreSpec extends Specification {

    private DagFlowReplay createReplay(String id) {
        return new DagFlowReplay(
                id,
                Instant.now(),
                Instant.now(),
                100L,
                2,
                true,
                [new NodeReplayRecord("node1", "AsyncCommand", "thread-1", [], 0, 50, 0, 50, true, null),
                 new NodeReplayRecord("node2", "CalcCommand", "thread-2", ["node1"], 50, 100, 50, 100, true, null)]
        )
    }

    def "store and retrieve replay record"() {
        given:
        def store = new DagFlowReplayStore(10)
        def replay = createReplay("abc")

        when:
        store.put(replay)

        then:
        store.get("abc") != null
        store.get("abc").id == "abc"
        store.size() == 1
    }

    def "get returns null for unknown id"() {
        given:
        def store = new DagFlowReplayStore(10)

        expect:
        store.get("nonexistent") == null
    }

    def "getAll returns all stored records"() {
        given:
        def store = new DagFlowReplayStore(10)
        store.put(createReplay("a"))
        store.put(createReplay("b"))
        store.put(createReplay("c"))

        when:
        def all = store.getAll()

        then:
        all.size() == 3
    }

    def "LRU eviction when exceeding max size"() {
        given:
        def store = new DagFlowReplayStore(3)

        when:
        store.put(createReplay("1"))
        store.put(createReplay("2"))
        store.put(createReplay("3"))
        store.put(createReplay("4"))

        then:
        store.size() == 3
        store.get("1") == null  // evicted (oldest)
        store.get("2") != null
        store.get("3") != null
        store.get("4") != null
    }

    def "LRU updates access order"() {
        given:
        def store = new DagFlowReplayStore(3)
        store.put(createReplay("1"))
        store.put(createReplay("2"))
        store.put(createReplay("3"))

        when: "access '1' to make it recent"
        store.get("1")
        store.put(createReplay("4"))

        then: "'2' is evicted (least recently used), '1' survives"
        store.size() == 3
        store.get("1") != null
        store.get("2") == null
        store.get("3") != null
        store.get("4") != null
    }

    def "clear removes all records"() {
        given:
        def store = new DagFlowReplayStore(10)
        store.put(createReplay("a"))
        store.put(createReplay("b"))

        when:
        store.clear()

        then:
        store.size() == 0
        store.getAll().isEmpty()
    }

    def "put null does nothing"() {
        given:
        def store = new DagFlowReplayStore(10)

        when:
        store.put(null)

        then:
        store.size() == 0
    }
}
