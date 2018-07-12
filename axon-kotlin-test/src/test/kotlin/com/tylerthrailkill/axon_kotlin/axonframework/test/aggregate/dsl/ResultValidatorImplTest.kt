package com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.dsl

import org.axonframework.eventhandling.EventMessage
import org.axonframework.eventhandling.GenericEventMessage.asEventMessage
import org.axonframework.test.AxonAssertionError
import org.axonframework.test.aggregate.ResultValidatorImpl
import org.axonframework.test.matchers.FieldFilter
import org.axonframework.test.matchers.MatchAllFieldFilter
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith


class ResultValidatorImplTest {

    private val validator = ResultValidatorImpl(actualEvents(), MatchAllFieldFilter(listOf<FieldFilter>()))

    @Test
    fun shouldCompareValuesForEquality() {
        assertFailsWith<AxonAssertionError> {
            val expected = actualEvents().iterator().next().andMetaData(mapOf<String, String>("key1" to "otherValue"))

            validator.expectEvents(expected)
        }
    }

    private fun actualEvents(): List<EventMessage<*>> {
        return listOf(asEventMessage<MyEvent>(MyEvent("aggregateId", 123))
                .andMetaData(mapOf<String, String>("key1" to "value1")))
    }

    @Test
    fun shouldCompareKeysForEquality() {
        assertFailsWith<AxonAssertionError> {
            val expected = actualEvents().iterator().next().andMetaData(mapOf<String, String>("KEY1" to "value1"))

            validator.expectEvents(expected)
        }
    }
}