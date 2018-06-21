package com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate

import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions.CreateAggregateCommand
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.commandhandling.TargetAggregateIdentifier
import org.axonframework.commandhandling.model.AggregateIdentifier
import org.axonframework.commandhandling.model.AggregateLifecycle
import org.axonframework.commandhandling.model.AggregateRoot
import org.axonframework.eventsourcing.EventSourcingHandler
import java.util.*


@AggregateRoot
class StubAnnotatedAggregate() {
    @AggregateIdentifier lateinit var identifier: UUID
    val ignoredField: String = ""

    @CommandHandler
    constructor(command: CreateStubAggregateCommand): this() {
        AggregateLifecycle.apply(AggregateCreatedEvent(command.id))
    }

    @CommandHandler
    fun handle(command: StubCommand) {
        AggregateLifecycle.apply(StubEvent(command.id))
    }

    @CommandHandler
    fun handle(command: ThrowExceptionCommand) {
        throw RuntimeException("with a message")
    }

    @EventSourcingHandler
    fun on(event: AggregateCreatedEvent) {
        identifier = event.id
    }

    @EventSourcingHandler
    fun on(event: StubEvent) {}
}

data class AggregateCreatedEvent(val id: UUID)
data class StubEvent(val id: UUID)

data class CreateStubAggregateCommand(@TargetAggregateIdentifier val id: UUID)
data class StubCommand(@TargetAggregateIdentifier val id: UUID)
data class ThrowExceptionCommand(@TargetAggregateIdentifier val id: UUID)


internal data class TestEvent(val aggregateIdentifier: Any, val values: Map<String, Any>)
