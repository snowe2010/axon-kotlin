package com.snowe.axon_kotlin.axonframework.test.aggregate.dsl

import com.snowe.axon_kotlin.axonframework.test.aggregate.AggregateTestFixture
import org.axonframework.eventsourcing.GenericDomainEventMessage
import org.axonframework.eventsourcing.eventstore.EventStoreException
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork
import org.axonframework.test.AxonAssertionError
import org.axonframework.test.FixtureExecutionException
import org.axonframework.test.aggregate.AggregateTestFixture
import org.axonframework.test.aggregate.FixtureConfiguration
import org.junit.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertTrue
import kotlin.test.fail


class FixtureTest_Annotated {

    private lateinit var fixture: FixtureConfiguration<AnnotatedAggregate>

    @BeforeEach
    fun setUp() {
        fixture = AggregateTestFixture<AnnotatedAggregate>()
    }

    @AfterEach
    fun tearDown() {
        if (CurrentUnitOfWork.isStarted()) {
            fail("A unit of work is still running")
        }
    }

    @Test
    fun testNullIdentifierIsRejected() {
        try {
            fixture!!.given(MyEvent(null, 0))
                    .`when`(TestCommand("test"))
                    .expectEvents(MyEvent("test", 1))
                    .expectSuccessfulHandlerExecution()
            fail("Expected test fixture to report failure")
        } catch (error: AxonAssertionError) {
            assertTrue(error.message!!.contains("IncompatibleAggregateException"), "Expected test to fail with IncompatibleAggregateException")
        }

    }

    @Test
    fun testAggregateCommandHandlersOverwrittenByCustomHandlers() {
        val invoked = AtomicBoolean(false)
        fixture!!.registerCommandHandler(CreateAggregateCommand::class.java) { commandMessage ->
            invoked.set(true)
            null
        }

        fixture!!.given().`when`(CreateAggregateCommand()).expectEvents()
        assertTrue("", invoked.get())
    }

    @Test
    fun testAggregateIdentifier_ServerGeneratedIdentifier() {
        fixture!!.registerInjectableResource(HardToCreateResource())
        fixture!!.given()
                .`when`(CreateAggregateCommand())
    }

    @Test(expected = FixtureExecutionException::class)
    fun testUnavailableResourcesCausesFailure() {
        fixture!!.given()
                .`when`(CreateAggregateCommand())
    }

    @Test
    fun testAggregateIdentifier_IdentifierAutomaticallyDeducted() {
        fixture!!.given(MyEvent("AggregateId", 1), MyEvent("AggregateId", 2))
                .`when`(TestCommand("AggregateId"))
                .expectEvents(MyEvent("AggregateId", 3))

        val events = fixture!!.getEventStore().readEvents("AggregateId")
        for (t in 0..2) {
            assertTrue(events.hasNext())
            val next = events.next()
            assertEquals("AggregateId", next.aggregateIdentifier)
            assertEquals(t, next.sequenceNumber)
        }
    }

    @Test(expected = FixtureExecutionException::class)
    fun testFixtureGivenCommands_ResourcesNotAvailable() {
        fixture!!.givenCommands(CreateAggregateCommand("aggregateId"))
    }

    @Test
    fun testFixtureGivenCommands_ResourcesAvailable() {
        fixture!!.registerInjectableResource(HardToCreateResource())
        fixture!!.givenCommands(CreateAggregateCommand("aggregateId"),
                TestCommand("aggregateId"),
                TestCommand("aggregateId"),
                TestCommand("aggregateId"))
                .`when`(TestCommand("aggregateId"))
                .expectEvents(MyEvent("aggregateId", 4))
    }

    @Test(expected = FixtureExecutionException::class)
    fun testAggregate_InjectCustomResourceAfterCreatingAnnotatedHandler() {
        // a 'when' will cause command handlers to be registered.
        fixture!!.registerInjectableResource(HardToCreateResource())
        fixture!!.given()
                .`when`(CreateAggregateCommand("AggregateId"))
        fixture!!.registerInjectableResource("I am injectable")
    }

    @Test(expected = EventStoreException::class)
    fun testFixtureGeneratesExceptionOnWrongEvents_DifferentAggregateIdentifiers() {
        fixture!!.getEventStore().publish(
                GenericDomainEventMessage("test", UUID.randomUUID().toString(), 0, StubDomainEvent()),
                GenericDomainEventMessage("test", UUID.randomUUID().toString(), 0, StubDomainEvent()))
    }

    @Test(expected = EventStoreException::class)
    fun testFixtureGeneratesExceptionOnWrongEvents_WrongSequence() {
        val identifier = UUID.randomUUID().toString()
        fixture!!.getEventStore().publish(
                GenericDomainEventMessage("test", identifier, 0, StubDomainEvent()),
                GenericDomainEventMessage("test", identifier, 2, StubDomainEvent()))
    }

    @Test
    fun testFixture_AggregateDeleted() {
        fixture!!.given(MyEvent("aggregateId", 5))
                .`when`(DeleteCommand("aggregateId", false))
                .expectEvents(MyAggregateDeletedEvent(false))
    }

    @Test
    fun testFixtureDetectsStateChangeOutsideOfHandler_AggregateDeleted() {
        val exec = fixture!!.given(MyEvent("aggregateId", 5))
        try {
            exec.`when`(DeleteCommand("aggregateId", true))
            fail("Fixture should have failed")
        } catch (error: AssertionError) {
            assertTrue("Wrong message: " + error.message, error.message.contains("considered deleted"))
        }

    }

    @Test
    fun testAndGiven() {
        fixture!!.registerInjectableResource(HardToCreateResource())
        fixture!!.givenCommands(CreateAggregateCommand("aggregateId"))
                .andGiven(MyEvent("aggregateId", 1))
                .`when`(TestCommand("aggregateId"))
                .expectEvents(MyEvent("aggregateId", 2))
    }

    @Test
    fun testAndGivenCommands() {
        fixture!!.given(MyEvent("aggregateId", 1))
                .andGivenCommands(TestCommand("aggregateId"))
                .`when`(TestCommand("aggregateId"))
                .expectEvents(MyEvent("aggregateId", 3))
    }

    @Test
    fun testMultipleAndGivenCommands() {
        fixture!!.given(MyEvent("aggregateId", 1))
                .andGivenCommands(TestCommand("aggregateId"))
                .andGivenCommands(TestCommand("aggregateId"))
                .`when`(TestCommand("aggregateId"))
                .expectEvents(MyEvent("aggregateId", 4))
    }

    private inner class StubDomainEvent
}
