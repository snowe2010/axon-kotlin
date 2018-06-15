package com.snowe.axon_kotlin.axonframework.test.aggregate

import com.nhaarman.mockito_kotlin.mock
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.messaging.MessageHandler
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
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
                .whenever(CreateAggregateCommand(id))
                .expectEvents(AggregateCreatedEvent(id))
    }

    @Test
    fun `Expect exception by class`() {
        val fixture = AggregateTestFixture<StubAnnotatedAggregate>()
        val id = UUID.randomUUID()
        fixture.givenCommands(CreateAggregateCommand(id))
                .whenever(ThrowExceptionCommand(id))
                .expectException<RuntimeException>()
    }

    @Test
    fun `Expect exception by class with message`() {
        val fixture = AggregateTestFixture<StubAnnotatedAggregate>()
        val id = UUID.randomUUID()
        fixture.givenCommands(CreateAggregateCommand(id))
                .whenever(ThrowExceptionCommand(id))
                .expectException<RuntimeException>("with a message")
    }

    @Test
    fun `Expect exception still allows chaining`() {
        val fixture = AggregateTestFixture<StubAnnotatedAggregate>()
        val id = UUID.randomUUID()
        fixture.givenCommands(CreateAggregateCommand(id))
                .whenever(ThrowExceptionCommand(id))
                .expectException<RuntimeException>()
                .expectException<RuntimeException>("with a message")
                .expectException(RuntimeException::class.java)
    }

}
