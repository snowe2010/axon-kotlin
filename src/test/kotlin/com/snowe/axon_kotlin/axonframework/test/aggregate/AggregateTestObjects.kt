package com.snowe.axon_kotlin.axonframework.test.aggregate

import org.axonframework.commandhandling.CommandHandler
import org.axonframework.commandhandling.TargetAggregateIdentifier
import org.axonframework.commandhandling.model.AggregateIdentifier
import org.axonframework.commandhandling.model.AggregateLifecycle
import org.axonframework.commandhandling.model.AggregateRoot
import org.axonframework.eventsourcing.EventSourcingHandler
import java.io.Serializable
import java.util.*


@AggregateRoot
class StubAnnotatedAggregate() {
    @AggregateIdentifier lateinit var identifier: UUID
    val ignoredField: String = ""

    @CommandHandler
    constructor(command: StubDomainCommand): this() {
        AggregateLifecycle.apply(StubDomainEvent(command.id))
    }

    @CommandHandler
    fun handle(command: ThrowExceptionCommand) {
        throw RuntimeException("with a message")
    }

    @EventSourcingHandler
    fun on(event: StubDomainEvent) {
        identifier = event.id
    }
}

data class StubDomainEvent(val id: UUID)

data class StubDomainCommand(@TargetAggregateIdentifier val id: UUID)
data class ThrowExceptionCommand(@TargetAggregateIdentifier val id: UUID)
