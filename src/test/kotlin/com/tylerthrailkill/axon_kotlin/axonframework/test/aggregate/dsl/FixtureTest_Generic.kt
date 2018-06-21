package com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.dsl

import com.nhaarman.mockito_kotlin.*
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.AggregateTestFixture
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.whenever
import org.axonframework.eventsourcing.AggregateFactory
import org.axonframework.eventsourcing.DomainEventMessage
import org.axonframework.eventsourcing.GenericDomainEventMessage
import org.axonframework.eventsourcing.IncompatibleAggregateException
import org.axonframework.eventsourcing.eventstore.EventStoreException
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork
import org.axonframework.test.FixtureExecutionException
import org.axonframework.test.aggregate.FixtureConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * @author Allard Buijze
 * @author Tyler Thrailkill
 */
class FixtureTest_Generic {

    private lateinit var fixture: FixtureConfiguration<StandardAggregate>
    private val mockAggregateFactory: AggregateFactory<StandardAggregate> = mock()

    @BeforeEach
    fun setUp() {
        fixture = AggregateTestFixture<StandardAggregate>()
        fixture.setReportIllegalStateChange(false)
        whenever(mockAggregateFactory.aggregateType).thenReturn(StandardAggregate::class.java)
        whenever(mockAggregateFactory.createAggregateRoot(isA(), isA()))
                .thenReturn(StandardAggregate("id1"))
    }

    @AfterEach
    fun tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            fail("Test failed to close Unit of Work!!")
            CurrentUnitOfWork.get().rollback()
        }

    }

    @Test
    fun testConfigureCustomAggregateFactory() {
        fixture {
            register {
                aggregateFactory = mockAggregateFactory
                annotatedCommandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
            }
            given {
                events { +MyEvent("id1", 1) }
            }
            whenever { TestCommand("id1") }
        }
        verify(mockAggregateFactory).createAggregateRoot(eq("id1"), isA<DomainEventMessage<*>>())
    }

    @Test
    fun testConfigurationOfRequiredCustomAggregateFactoryNotProvided_FailureOnGiven() {
        assertFailsWith<IncompatibleAggregateException> {
            fixture.given(MyEvent("id1", 1))
        }
    }

    @Test
    fun testConfigurationOfRequiredCustomAggregateFactoryNotProvided_FailureOnGetRepository() {
        assertFailsWith<IncompatibleAggregateException> {
            fixture.repository
        }
    }

    @Test
    fun testAggregateIdentifier_ServerGeneratedIdentifier() {
        fixture.registerAggregateFactory(mockAggregateFactory)
        fixture.registerAnnotatedCommandHandler(MyCommandHandler(fixture.repository, fixture.eventBus))
        fixture.givenNoPriorActivity()
                .whenever(CreateAggregateCommand())
    }

    @Test
    fun testStoringExistingAggregateGeneratesException() {
        fixture.registerAggregateFactory(mockAggregateFactory)
        fixture.registerAnnotatedCommandHandler(MyCommandHandler(fixture.repository, fixture.eventBus))
        fixture.given(MyEvent("aggregateId", 1))
                .`when`(CreateAggregateCommand("aggregateId"))
                .expectException(EventStoreException::class.java)
    }

    @Test
    fun testInjectResources_CommandHandlerAlreadyRegistered() {
        assertFailsWith<FixtureExecutionException> {
            fixture.registerAggregateFactory(mockAggregateFactory)
            fixture.registerAnnotatedCommandHandler(MyCommandHandler(fixture.repository, fixture.eventBus))
            fixture.registerInjectableResource("I am injectable")
        }
    }

    @Test
    fun testAggregateIdentifier_IdentifierAutomaticallyDeducted() {
        fixture.registerAggregateFactory(mockAggregateFactory)
        fixture.registerAnnotatedCommandHandler(MyCommandHandler(fixture.repository, fixture.eventBus))
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
    fun testReadAggregate_WrongIdentifier() {
        fixture.registerAggregateFactory(mockAggregateFactory)
        fixture.registerAnnotatedCommandHandler(MyCommandHandler(fixture.repository, fixture.eventBus))
        val exec = fixture.given(MyEvent("AggregateId", 1))
        try {
            exec.whenever(TestCommand("OtherIdentifier"))
            fail("Expected an AssertionError")
        } catch (e: AssertionError) {
            assertTrue(e.message!!.contains("OtherIdentifier"), "Wrong message. Was: " + e.message)
            assertTrue(e.message!!.contains("AggregateId"), "Wrong message. Was: " + e.message)
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

    private inner class StubDomainEvent
}