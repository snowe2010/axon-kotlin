package com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions

import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.AggregateTestFixture
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.whenever
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.eventhandling.EventMessage
import org.axonframework.messaging.MessageHandler
import org.axonframework.test.AxonAssertionError
import org.axonframework.test.aggregate.FixtureConfiguration
import org.axonframework.test.matchers.Matchers.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Heavily borrowed from AxonFramework will permission from Allard Buijze and Steven Van Beelen
 */
class FixtureTest_MatcherParams {
    private lateinit var fixture: FixtureConfiguration<StandardAggregate>

    @BeforeEach
    fun beforeAll() {
        fixture = AggregateTestFixture<StandardAggregate>()
        fixture.registerAggregateFactory(StandardAggregate.Factory())
    }

    @Test
    fun testFirstFixture() {
        fixture.registerAnnotatedCommandHandler(MyCommandHandler(fixture.repository, fixture.eventBus))
                .given(MyEvent("aggregateId", 1))
                .whenever(TestCommand("aggregateId"))
                .expectReturnValueMatching(DoesMatch<Any>())
                .expectEventsMatching(sequenceOf<EventMessage<*>>(matches<EventMessage<*>> { true }))
    }

    @Test
    fun testPayloadsMatch() {
        fixture.registerAnnotatedCommandHandler(MyCommandHandler(fixture.repository, fixture.eventBus))
                .given(MyEvent("aggregateId", 1))
                .whenever(TestCommand("aggregateId"))
                .expectReturnValueMatching(DoesMatch<Any>())
                .expectEventsMatching(payloadsMatching(sequenceOf<Any>(matches<Any> { true })))
    }

    @Test
    fun testPayloadsMatchExact() {
        fixture.registerAnnotatedCommandHandler(MyCommandHandler(fixture.repository, fixture.eventBus))
                .given(MyEvent("aggregateId", 1))
                .whenever(TestCommand("aggregateId"))
                .expectReturnValueMatching(DoesMatch<Any>())
                .expectEventsMatching(payloadsMatching(exactSequenceOf(matches<Any> { true })))
    }

    @Test
    fun testPayloadsMatchPredicate() {
        fixture.registerAnnotatedCommandHandler(MyCommandHandler(fixture.repository, fixture.eventBus))
                .given(MyEvent("aggregateId", 1))
                .whenever(TestCommand("aggregateId"))
                .expectReturnValueMatching(DoesMatch<Any>())
                .expectEventsMatching(payloadsMatching(predicate { ml -> !ml.isEmpty() }))
    }

    @Test
    fun testFixture_UnexpectedException() {
        val givenEvents = listOf (MyEvent("aggregateId", 1),
                MyEvent("aggregateId", 2),
                MyEvent("aggregateId", 3))
        val commandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
        try {
            fixture.registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .whenever(StrangeCommand("aggregateId"))
                    .expectReturnValueMatching(DoesMatch<Any>())
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("but got <exception of type [StrangeCommandReceivedException]>"))
        }

    }

    @Test
    fun testFixture_UnexpectedReturnValue() {
        val givenEvents = listOf(MyEvent("aggregateId", 1),
                MyEvent("aggregateId", 2),
                MyEvent("aggregateId", 3))
        val commandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
        try {
            fixture.registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .whenever(TestCommand("aggregateId"))
                    .expectException(DoesMatch<Any>())
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("The command handler returned normally, but an exception was expected"))
            assertTrue(e.message!!.contains("<anything> but returned with <null>"))
        }

    }

    @Test
    fun testFixture_WrongReturnValue() {
        val givenEvents = listOf(MyEvent("aggregateId", 1),
                MyEvent("aggregateId", 2),
                MyEvent("aggregateId", 3))
        val commandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
        try {
            fixture.registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .whenever(TestCommand("aggregateId"))
                    .expectReturnValueMatching(DoesNotMatch<Any>())
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("<something you can never give me> but got <null>"))
        }

    }

    @Test
    fun testFixture_WrongExceptionType() {
        val givenEvents = listOf(MyEvent("aggregateId", 1),
                MyEvent("aggregateId", 2),
                MyEvent("aggregateId", 3))
        val commandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
        try {
            fixture.registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .whenever(StrangeCommand("aggregateId"))
                    .expectException(DoesNotMatch<Any>())
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains(
                    "<something you can never give me> but got <exception of type [StrangeCommandReceivedException]>"))
        }

    }

    @Test
    fun testFixture_ExpectedPublishedSameAsStored() {
        val givenEvents = listOf(MyEvent("aggregateId", 1),
                MyEvent("aggregateId", 2),
                MyEvent("aggregateId", 3))
        val commandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
        try {
            fixture.registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .whenever(StrangeCommand("aggregateId"))
                    .expectEvents(DoesMatch<List<EventMessage<*>>>())
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("The published events do not match the expected events"))
            assertTrue(e.message!!.contains("com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions.DoesMatch <|> "))
            assertTrue(e.message!!.contains("probable cause"))
        }

    }

    @Test
    @Throws(Exception::class)
    fun testFixture_DispatchMetaDataInCommand() {
        val givenEvents = listOf(MyEvent("aggregateId", 1), MyEvent("aggregateId", 2),
                MyEvent("aggregateId", 3))
        val mockCommandHandler = mock<MessageHandler<CommandMessage<*>>>()
        fixture.registerCommandHandler(StrangeCommand::class.java, mockCommandHandler)
        fixture.given(givenEvents)
                .whenever(StrangeCommand("aggregateId"), mapOf("meta" to "value"))

        val captor = argumentCaptor<CommandMessage<*>>()
        verify(mockCommandHandler).handle(captor.capture())
        val dispatched = captor.allValues
        assertEquals(1, dispatched.size)
        assertEquals(1, dispatched[0].metaData.size)
        assertEquals("value", dispatched[0].metaData["meta"])
    }

    @Test
    fun testFixture_EventDoesNotMatch() {
        val givenEvents = listOf(MyEvent("aggregateId", 1),
                MyEvent("aggregateId", 2),
                MyEvent("aggregateId", 3))
        val commandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
        try {
            fixture.registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .whenever(TestCommand("aggregateId"))
                    .expectEventsMatching(DoesNotMatch<MutableList<in EventMessage<*>>>())
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("something you can never give me"), "Wrong message: " + e.message)
        }

    }

}