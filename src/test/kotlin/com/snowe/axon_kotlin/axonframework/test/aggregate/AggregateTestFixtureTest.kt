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
}

@AggregateRoot
data class StubAnnotatedAggregate(@field:AggregateIdentifier val identifier: UUID, val ignoredField: String) {
    fun doSomething() {
        apply(StubDomainEvent())
    }
}

class StubDomainEvent : Serializable {
    override fun toString(): String {
        return "StubDomainEvent"
    }

    companion object {
        private const val serialVersionUID = 834667054977749990L
    }
}



