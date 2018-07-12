package com.tylerthrailkill.axon_kotlin.axonframework.test.saga.dsl


import org.axonframework.eventhandling.GenericEventMessage
import org.axonframework.test.AxonAssertionError
import org.axonframework.test.matchers.AllFieldsFilter
import org.axonframework.test.matchers.Matchers
import org.axonframework.test.saga.EventValidator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class EventValidatorTest {

    private var testSubject: EventValidator? = null

    @BeforeEach
    fun setUp() {
        testSubject = EventValidator(null, AllFieldsFilter.instance())
    }

    @Test
    fun testAssertPublishedEventsWithNoEventsMatcherIfNoEventWasPublished() {
        testSubject!!.assertPublishedEventsMatching(Matchers.noEvents())
    }

    @Test
    fun testAssertPublishedEventsWithNoEventsMatcherThrowsAssertionErrorIfEventWasPublished() {
        assertFailsWith<AxonAssertionError> {
            testSubject!!.handle(GenericEventMessage.asEventMessage<MyOtherEvent>(MyOtherEvent()))

            testSubject!!.assertPublishedEventsMatching(Matchers.noEvents())
        }
    }

    @Test
    fun testAssertPublishedEventsIfNoEventWasPublished() {
        testSubject!!.assertPublishedEvents()
    }

    @Test
    fun testAssertPublishedEventsThrowsAssertionErrorIfEventWasPublished() {
        assertFailsWith<AxonAssertionError> {
            testSubject!!.handle(GenericEventMessage.asEventMessage<MyOtherEvent>(MyOtherEvent()))

            testSubject!!.assertPublishedEvents()
        }
    }

}