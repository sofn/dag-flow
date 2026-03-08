package com.lesofn.dagflow.spring.boot

import com.lesofn.dagflow.spring.boot.autoconfigure.DagFlowProperties
import spock.lang.Specification

class DagFlowReplayPropertiesSpec extends Specification {

    def "replay defaults to disabled"() {
        given:
        def props = new DagFlowProperties()

        expect:
        !props.replayEnabled
    }

    def "replay cache size defaults to 100"() {
        given:
        def props = new DagFlowProperties()

        expect:
        props.replayCacheSize == 100
    }

    def "replay properties can be customized"() {
        given:
        def props = new DagFlowProperties()

        when:
        props.replayEnabled = true
        props.replayCacheSize = 50

        then:
        props.replayEnabled
        props.replayCacheSize == 50
    }
}
