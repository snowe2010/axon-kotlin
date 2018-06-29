package com.tylerthrailkill.axon_kotlin.axonframework.test.saga.dsl


import org.axonframework.commandhandling.GenericCommandMessage
import org.axonframework.eventhandling.EventMessage
import org.axonframework.eventhandling.GenericEventMessage
import org.axonframework.eventhandling.SimpleEventBus
import org.axonframework.eventhandling.saga.AssociationValue
import org.axonframework.eventhandling.saga.repository.inmemory.InMemorySagaStore
import org.axonframework.test.AxonAssertionError
import org.axonframework.test.eventscheduler.StubEventScheduler
import org.axonframework.test.matchers.AllFieldsFilter
import org.axonframework.test.matchers.Matchers.andNoMore
import org.axonframework.test.matchers.Matchers.equalTo
import org.axonframework.test.matchers.Matchers.exactSequenceOf
import org.axonframework.test.matchers.Matchers.payloadsMatching
import org.axonframework.test.utils.RecordingCommandBus
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.*
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * @author Allard Buijze
 */
class FixtureExecutionResultImplTest {

    private lateinit var testSubject: FixtureExecutionResultImpl<StubSaga>
    private lateinit var commandBus: RecordingCommandBus
    private lateinit var eventBus: SimpleEventBus
    private lateinit var eventScheduler: StubEventScheduler
    private lateinit var sagaStore: InMemorySagaStore
    private lateinit var applicationEvent: TimerTriggeredEvent
    private lateinit var identifier: String

    @BeforeEach
    fun setUp() {
        commandBus = RecordingCommandBus()
        eventBus = SimpleEventBus()
        eventScheduler = StubEventScheduler()
        sagaStore = InMemorySagaStore()
        testSubject = FixtureExecutionResultImpl(sagaStore, eventScheduler, eventBus,
                commandBus, StubSaga::class.java, AllFieldsFilter.instance())
        testSubject.startRecording()
        identifier = UUID.randomUUID().toString()
        applicationEvent = TimerTriggeredEvent(identifier)
    }

    @Test
    fun testStartRecording() {
        testSubject = FixtureExecutionResultImpl<StubSaga>(sagaStore, eventScheduler, eventBus,
                commandBus, StubSaga::class.java, AllFieldsFilter.instance())
        commandBus.dispatch(GenericCommandMessage.asCommandMessage<Any>("First"))
        eventBus.publish(GenericEventMessage<Any>(TriggerSagaStartEvent(identifier)))
        testSubject.startRecording()
        val endEvent = TriggerSagaEndEvent(identifier)
        eventBus.publish(GenericEventMessage<Any>(endEvent))
        commandBus.dispatch(GenericCommandMessage.asCommandMessage<Any>("Second"))

        testSubject.expectPublishedEvents(endEvent)
        testSubject.expectPublishedEventsMatching(payloadsMatching(exactSequenceOf(equalTo(endEvent), andNoMore<Any>())))

        testSubject.expectDispatchedCommands("Second")
        testSubject.expectDispatchedCommandsMatching(payloadsMatching(exactSequenceOf(equalTo("Second"), andNoMore<Any>())))
    }

    @Test
    fun testExpectPublishedEvents_WrongCount() {
        assertFailsWith<AxonAssertionError> {
            eventBus.publish(GenericEventMessage<Any>(TriggerSagaEndEvent(identifier)))

            testSubject.expectPublishedEvents(TriggerSagaEndEvent(identifier),
                    TriggerExistingSagaEvent(identifier))
        }
    }

    @Test
    fun testExpectPublishedEvents_WrongType() {
        assertFailsWith<AxonAssertionError> {
            eventBus.publish(GenericEventMessage<Any>(TriggerSagaEndEvent(identifier)))

            testSubject.expectPublishedEvents(TriggerExistingSagaEvent(identifier))
        }
    }

    @Test
    fun testExpectPublishedEvents_FailedMatcher() {
        assertFailsWith<AxonAssertionError> {
            eventBus.publish(GenericEventMessage<Any>(TriggerSagaEndEvent(identifier)))

            testSubject.expectPublishedEvents(FailingMatcher<EventMessage<*>>())
        }
    }

    @Test
    fun testExpectDispatchedCommands_FailedCount() {
        assertFailsWith<AxonAssertionError> {
            commandBus.dispatch(GenericCommandMessage.asCommandMessage<Any>("First"))
            commandBus.dispatch(GenericCommandMessage.asCommandMessage<Any>("Second"))
            commandBus.dispatch(GenericCommandMessage.asCommandMessage<Any>("Third"))
            commandBus.dispatch(GenericCommandMessage.asCommandMessage<Any>("Fourth"))

            testSubject.expectDispatchedCommands("First", "Second", "Third")
        }
    }

    @Test
    fun testExpectDispatchedCommands_FailedType() {
        assertFailsWith<AxonAssertionError> {
            commandBus.dispatch(GenericCommandMessage.asCommandMessage<Any>("First"))
            commandBus.dispatch(GenericCommandMessage.asCommandMessage<Any>("Second"))

            testSubject.expectDispatchedCommands("First", "Third")
        }
    }

    @Test
    fun testExpectDispatchedCommands() {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage<Any>("First"))
        commandBus.dispatch(GenericCommandMessage.asCommandMessage<Any>("Second"))

        testSubject.expectDispatchedCommands("First", "Second")
    }

    @Test
    fun testExpectDispatchedCommands_ObjectsNotImplementingEquals() {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage<Any>(SimpleCommand("First")))
        commandBus.dispatch(GenericCommandMessage.asCommandMessage<Any>(SimpleCommand("Second")))

        testSubject.expectDispatchedCommands(SimpleCommand("First"), SimpleCommand("Second"))
    }

    @Test
    fun testExpectDispatchedCommands_ObjectsNotImplementingEquals_FailedField() {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage<Any>(SimpleCommand("First")))
        commandBus.dispatch(GenericCommandMessage.asCommandMessage<Any>(SimpleCommand("Second")))

        try {
            testSubject.expectDispatchedCommands(SimpleCommand("Second"), SimpleCommand("Thrid"))
            fail("Expected exception")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("expected <Second>"), "Wrong message: " + e.message)
        }

    }

    @Test
    fun testExpectDispatchedCommands_ObjectsNotImplementingEquals_WrongType() {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage<Any>(SimpleCommand("First")))
        commandBus.dispatch(GenericCommandMessage.asCommandMessage<Any>(SimpleCommand("Second")))

        try {
            testSubject.expectDispatchedCommands("Second", SimpleCommand("Thrid"))
            fail("Expected exception")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("Expected <String>"), "Wrong message: " + e.message)
        }

    }

    @Test
    fun testExpectNoDispatchedCommands_Failed() {
        assertFailsWith<AxonAssertionError> {
            commandBus.dispatch(GenericCommandMessage.asCommandMessage<Any>("First"))
            testSubject.expectNoDispatchedCommands()
        }
    }

    @Test
    fun testExpectNoDispatchedCommands() {
        testSubject.expectNoDispatchedCommands()
    }

    @Test
    fun testExpectDispatchedCommands_FailedMatcher() {
        assertFailsWith<AxonAssertionError> {
            testSubject.expectDispatchedCommands(FailingMatcher<String>())
        }
    }

    @Test
    fun testExpectNoScheduledEvents_EventIsScheduled() {
        assertFailsWith<AxonAssertionError> {
            eventScheduler.schedule(Duration.ofSeconds(1), GenericEventMessage<Any>(
                    applicationEvent))
            testSubject.expectNoScheduledEvents()
        }
    }

    @Test
    fun testExpectNoScheduledEvents_NoEventScheduled() {
        testSubject.expectNoScheduledEvents()
    }

    @Test
    fun testExpectNoScheduledEvents_ScheduledEventIsTriggered() {
        eventScheduler.schedule(Duration.ofSeconds(1), GenericEventMessage<Any>(
                applicationEvent))
        eventScheduler.advanceToNextTrigger()
        testSubject.expectNoScheduledEvents()
    }

    @Test
    @Throws(Exception::class)
    fun testExpectScheduledEvent_WrongDateTime() {
        assertFailsWith<AxonAssertionError> {
            eventScheduler.schedule(Duration.ofSeconds(1), GenericEventMessage<Any>(
                    applicationEvent))
            eventScheduler.advanceTimeBy(Duration.ofMillis(500)) { i -> }
            testSubject.expectScheduledEvent(Duration.ofSeconds(1), applicationEvent)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testExpectScheduledEvent_WrongClass() {
        assertFailsWith<AxonAssertionError> {
            eventScheduler.schedule(Duration.ofSeconds(1), GenericEventMessage<Any>(applicationEvent))
            eventScheduler.advanceTimeBy(Duration.ofMillis(500)) { i -> }
            testSubject.expectScheduledEventOfType(Duration.ofSeconds(1), Any::class.java)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testExpectScheduledEvent_WrongEvent() {
        assertFailsWith<AxonAssertionError> {
            eventScheduler.schedule(Duration.ofSeconds(1),
                    GenericEventMessage<Any>(applicationEvent))
            eventScheduler.advanceTimeBy(Duration.ofMillis(500)) { i -> }
            testSubject.expectScheduledEvent(Duration.ofSeconds(1),
                    GenericEventMessage<Any>(TimerTriggeredEvent(
                            "unexpected")))
        }
    }

    @Test
    @Throws(Exception::class)
    fun testExpectScheduledEvent_FailedMatcher() {
        assertFailsWith<AxonAssertionError> {
            eventScheduler.schedule(Duration.ofSeconds(1), GenericEventMessage<Any>(
                    applicationEvent))
            eventScheduler.advanceTimeBy(Duration.ofMillis(500)) { i -> }
            testSubject.expectScheduledEvent(Duration.ofSeconds(1), FailingMatcher<StubSaga>())
        }
    }

    @Test
    @Throws(Exception::class)
    fun testExpectScheduledEvent_Found() {
        eventScheduler.schedule(Duration.ofSeconds(1), GenericEventMessage<Any>(
                applicationEvent))
        eventScheduler.advanceTimeBy(Duration.ofMillis(500)) { i -> }
        testSubject.expectScheduledEvent(Duration.ofMillis(500), applicationEvent)
    }

    @Test
    fun testExpectScheduledEvent_FoundInMultipleCandidates() {
        eventScheduler.schedule(Duration.ofSeconds(1),
                GenericEventMessage<Any>(TimerTriggeredEvent("unexpected1")))
        eventScheduler.schedule(Duration.ofSeconds(1),
                GenericEventMessage<Any>(applicationEvent))
        eventScheduler.schedule(Duration.ofSeconds(1),
                GenericEventMessage<Any>(TimerTriggeredEvent("unexpected2")))
        testSubject.expectScheduledEvent(Duration.ofSeconds(1), applicationEvent)
    }

    @Test
    fun testAssociationWith_WrongValue() {
        assertFailsWith<AxonAssertionError> {
            sagaStore.insertSaga(StubSaga::class.java, "test", StubSaga(), null, setOf(AssociationValue("key", "value")))

            testSubject.expectAssociationWith("key", "value2")
        }
    }

    @Test
    fun testAssociationWith_WrongKey() {
        assertFailsWith<AxonAssertionError> {
            sagaStore.insertSaga(StubSaga::class.java, "test", StubSaga(), null, setOf(AssociationValue("key", "value")))

            testSubject.expectAssociationWith("key2", "value")
        }
    }

    @Test
    fun testAssociationWith_Present() {
        sagaStore.insertSaga(StubSaga::class.java, "test", StubSaga(), null, setOf(AssociationValue("key", "value")))

        testSubject.expectAssociationWith("key", "value")
    }

    @Test
    fun testNoAssociationWith_WrongValue() {
        sagaStore.insertSaga(StubSaga::class.java, "test", StubSaga(), null, setOf(AssociationValue("key", "value")))

        testSubject.expectNoAssociationWith("key", "value2")
    }

    @Test
    fun testNoAssociationWith_WrongKey() {
        sagaStore.insertSaga(StubSaga::class.java, "test", StubSaga(), null, setOf(AssociationValue("key", "value")))

        testSubject.expectNoAssociationWith("key2", "value")
    }

    @Test
    fun testNoAssociationWith_Present() {
        assertFailsWith<AxonAssertionError> {
            sagaStore.insertSaga(StubSaga::class.java, "test", StubSaga(), null, setOf(AssociationValue("key", "value")))

            testSubject.expectNoAssociationWith("key", "value")
        }
    }

    @Test
    fun testExpectActiveSagas_WrongCount() {
        assertFailsWith<AxonAssertionError> {
            sagaStore.insertSaga(StubSaga::class.java, "test", StubSaga(), null, emptySet())

            testSubject.expectActiveSagas(2)
        }
    }

    @Test
    fun testExpectActiveSagas_CorrectCount() {
        sagaStore.insertSaga(StubSaga::class.java, "test", StubSaga(), null, emptySet())
        sagaStore.deleteSaga(StubSaga::class.java, "test", emptySet())
        sagaStore.insertSaga(StubSaga::class.java, "test2", StubSaga(), null, emptySet())

        testSubject.expectActiveSagas(1)
    }

    private class SimpleCommand(private val content: String)

    private inner class FailingMatcher<T> : BaseMatcher<List<T>>() {

        override fun matches(item: Any): Boolean {
            return false
        }

        override fun describeTo(description: Description) {
            description.appendText("something you'll never be able to deliver")
        }
    }
}
