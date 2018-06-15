package com.snowe.axon_kotlin.axonframework.test.aggregate

import com.nhaarman.mockito_kotlin.mock
import com.snowe.axon_kotlin.axonframework.test.saga.SagaTestFixture
import com.snowe.axon_kotlin.axonframework.test.saga.registerCommandGateway
import com.snowe.axon_kotlin.axonframework.test.saga.registerIgnoredField
import com.snowe.axon_kotlin.events.FooCreatedEvent
import com.snowe.axon_kotlin.events.FooDeletedEvent
import com.snowe.axon_kotlin.events.FooIncrementedEvent
import org.axonframework.commandhandling.CommandBus
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.commandhandling.TargetAggregateIdentifier
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.commandhandling.gateway.DefaultCommandGateway
import org.axonframework.commandhandling.model.AggregateIdentifier
import org.axonframework.commandhandling.model.AggregateLifecycle
import org.axonframework.commandhandling.model.AggregateLifecycle.apply
import org.axonframework.commandhandling.model.AggregateRoot
import org.axonframework.eventhandling.EventMessage
import org.axonframework.eventhandling.saga.EndSaga
import org.axonframework.eventhandling.saga.SagaEventHandler
import org.axonframework.eventsourcing.DomainEventMessage
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.messaging.MessageHandler
import org.axonframework.test.aggregate.ResultValidator
import org.axonframework.test.aggregate.TestExecutor
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.Serializable
import java.util.*

/**
 * @author Tyler Thrailkill
 */

class AggregateTestFixtureTest {

    @Test
    fun `Fixture created with inline function`() {
        val fixture = AggregateTestFixture<StubAnnotatedAggregate>()
        Assertions.assertNotNull(fixture)
    }

    @Test
    fun `Register of command gateway`() {
        val fixture = AggregateTestFixture<StubAnnotatedAggregate>()
        val messageHandler = mock<MessageHandler<CommandMessage<*>>>()
        val gateway = fixture.registerCommandHandler<CommandHandler>(messageHandler)
        Assertions.assertNotNull(gateway)
    }

    @Test
    fun `Register ignored field`() {
        val fixture = AggregateTestFixture<StubAnnotatedAggregate>()
        val fixtureConfiguration = fixture.registerIgnoredField<StubAnnotatedAggregate>("ignoredField")
        Assertions.assertNotNull(fixtureConfiguration)
    }


    @Test
    fun `Overridden whenever forwards command properly`() {
        val fixture = AggregateTestFixture<StubAnnotatedAggregate>()
        val id = UUID.randomUUID()
        fixture.givenNoPriorActivity()
                .whenever(StubDomainCommand(id))
                .expectEvents(StubDomainEvent(id))
    }

    @Test
    fun `Expect exception by class`() {
        val fixture = AggregateTestFixture<StubAnnotatedAggregate>()
        val id = UUID.randomUUID()
        fixture.givenCommands(StubDomainCommand(id))
                .whenever(ThrowExceptionCommand(id))
                .expectException<RuntimeException>()
    }

    @Test
    fun `Expect exception by class with message`() {
        val fixture = AggregateTestFixture<StubAnnotatedAggregate>()
        val id = UUID.randomUUID()
        fixture.givenCommands(StubDomainCommand(id))
                .whenever(ThrowExceptionCommand(id))
                .expectException<RuntimeException>("with a message")
    }

    @Test
    fun `Expect exception still allows chaining`() {
        val fixture = AggregateTestFixture<StubAnnotatedAggregate>()
        val id = UUID.randomUUID()
        fixture.givenCommands(StubDomainCommand(id))
                .whenever(ThrowExceptionCommand(id))
                .expectException<RuntimeException>()
                .expectException<RuntimeException>("with a message")
                .expectException(RuntimeException::class.java)
    }

}
