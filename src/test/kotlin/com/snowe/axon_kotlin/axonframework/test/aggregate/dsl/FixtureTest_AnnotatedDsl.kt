package com.snowe.axon_kotlin.axonframework.test.aggregate.dsl

import com.snowe.axon_kotlin.axonframework.test.aggregate.AggregateTestFixture
import com.snowe.axon_kotlin.axonframework.test.aggregate.whenever
import org.axonframework.eventsourcing.GenericDomainEventMessage
import org.axonframework.eventsourcing.eventstore.EventStoreException
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork
import org.axonframework.test.AxonAssertionError
import org.axonframework.test.FixtureExecutionException
import org.axonframework.test.aggregate.FixtureConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
            fixture.given(MyEvent(null, 0))
                    .whenever(TestCommand("test"))
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
        fixture.registerCommandHandler(CreateAggregateCommand::class.java) {
            invoked.set(true)
            null
        }

        fixture.given().whenever(CreateAggregateCommand()).expectEvents()
        assertTrue(invoked.get())
    }

    @Test
    fun testAggregateIdentifier_ServerGeneratedIdentifier() {
        fixture.registerInjectableResource(HardToCreateResource())
        fixture.given()
                .whenever(CreateAggregateCommand())
    }

    @Test
    fun testUnavailableResourcesCausesFailure() {
        assertFailsWith<FixtureExecutionException> {
            fixture.given()
                    .whenever(CreateAggregateCommand())
        }
    }

    @Test
    fun testAggregateIdentifier_IdentifierAutomaticallyDeducted() {
        fixture.given(MyEvent("AggregateId", 1), MyEvent("AggregateId", 2))
                .whenever(TestCommand("AggregateId"))
                .expectEvents(MyEvent("AggregateId", 3))

        val events = fixture.eventStore.readEvents("AggregateId")
        for (t in 0..2) {
            assertTrue(events.hasNext())
            val next = events.next()
            assertEquals("AggregateId", next.aggregateIdentifier)
            assertEquals(t.toLong(), next.sequenceNumber)
        }
    }

    @Test
    fun testFixtureGivenCommands_ResourcesNotAvailable() {
        assertFailsWith<FixtureExecutionException> {
            fixture.givenCommands(CreateAggregateCommand("aggregateId"))
        }
    }

    @Test
    fun testFixtureGivenCommands_ResourcesAvailable() {
        fixture.registerInjectableResource(HardToCreateResource())
        fixture.givenCommands(CreateAggregateCommand("aggregateId"),
                TestCommand("aggregateId"),
                TestCommand("aggregateId"),
                TestCommand("aggregateId"))
                .whenever(TestCommand("aggregateId"))
                .expectEvents(MyEvent("aggregateId", 4))
    }

    @Test
    fun testAggregate_InjectCustomResourceAfterCreatingAnnotatedHandler() {
        // a 'when' will cause command handlers to be registered.
        assertFailsWith<FixtureExecutionException> {
            fixture.registerInjectableResource(HardToCreateResource())
            fixture.given()
                    .whenever(CreateAggregateCommand("AggregateId"))
            fixture.registerInjectableResource("I am injectable")
        }
    }

    @Test
    fun testFixtureGeneratesExceptionOnWrongEvents_DifferentAggregateIdentifiers() {
        assertFailsWith<EventStoreException> {
            fixture.eventStore.publish(
                    GenericDomainEventMessage("test", UUID.randomUUID().toString(), 0, StubDomainEvent()),
                    GenericDomainEventMessage("test", UUID.randomUUID().toString(), 0, StubDomainEvent()))
        }
    }

    @Test
    fun testFixtureGeneratesExceptionOnWrongEvents_WrongSequence() {
        assertFailsWith<EventStoreException> {
            val identifier = UUID.randomUUID().toString()
            fixture.eventStore.publish(
                    GenericDomainEventMessage("test", identifier, 0, StubDomainEvent()),
                    GenericDomainEventMessage("test", identifier, 2, StubDomainEvent()))
        }
    }

    @Test
    fun testFixture_AggregateDeleted() {
        fixture.given(MyEvent("aggregateId", 5))
                .whenever(DeleteCommand("aggregateId", false))
                .expectEvents(MyAggregateDeletedEvent(false))
    }

    @Test
    fun testFixtureDetectsStateChangeOutsideOfHandler_AggregateDeleted() {
        val exec = fixture.given(MyEvent("aggregateId", 5))
        try {
            exec.whenever(DeleteCommand("aggregateId", true))
            fail("Fixture should have failed")
        } catch (error: AssertionError) {
            assertTrue(error.message!!.contains("considered deleted"), "Wrong message: " + error.message)
        }

    }

    @Test
    fun testAndGiven() {
        fixture.registerInjectableResource(HardToCreateResource())
        fixture.givenCommands(CreateAggregateCommand("aggregateId"))
                .andGiven(MyEvent("aggregateId", 1))
                .whenever(TestCommand("aggregateId"))
                .expectEvents(MyEvent("aggregateId", 2))
    }

    @Test
    fun testAndGivenCommands() {
        fixture.given(MyEvent("aggregateId", 1))
                .andGivenCommands(TestCommand("aggregateId"))
                .whenever(TestCommand("aggregateId"))
                .expectEvents(MyEvent("aggregateId", 3))
    }

    @Test
    fun testMultipleAndGivenCommands() {
        fixture.given(MyEvent("aggregateId", 1))
                .andGivenCommands(TestCommand("aggregateId"))
                .andGivenCommands(TestCommand("aggregateId"))
                .whenever(TestCommand("aggregateId"))
                .expectEvents(MyEvent("aggregateId", 4))
    }

    private inner class StubDomainEvent
}
