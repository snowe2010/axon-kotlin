package com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions

import org.axonframework.commandhandling.CommandHandler
import org.axonframework.commandhandling.model.AggregateIdentifier
import org.axonframework.eventhandling.EventBus
import org.axonframework.eventsourcing.DomainEventMessage
import org.axonframework.eventsourcing.EventSourcingHandler
import java.util.*
import kotlin.test.assertNotNull
import org.axonframework.commandhandling.model.AggregateLifecycle.apply
import org.axonframework.commandhandling.model.AggregateLifecycle.markDeleted

internal interface AnnotatedAggregateInterface {

    @CommandHandler
    fun doSomething(command: TestCommand)
}


class HardToCreateResource
internal class CreateAggregateCommand @JvmOverloads constructor(val aggregateIdentifier: Any? = null)

internal class AnnotatedAggregate() : AnnotatedAggregateInterface {

    @Transient
    private var counter: Int = 0
    private var lastNumber: Int? = null
    @AggregateIdentifier
    private var identifier: String? = null
    private var entity: MyEntity? = null

    constructor(identifier: Any) : this() {
        this.identifier = identifier.toString()
    }

    @CommandHandler
    constructor(command: CreateAggregateCommand, eventBus: EventBus, resource: HardToCreateResource) : this() {
        assertNotNull(resource, "resource should not be null")
        assertNotNull(eventBus, "Expected EventBus to be injected as resource")
        apply(MyEvent(command.aggregateIdentifier ?: UUID.randomUUID(), 0))
    }

    @CommandHandler
    fun delete(command: DeleteCommand) {
        apply(MyAggregateDeletedEvent(command.isAsIllegalChange))
        if (command.isAsIllegalChange) {
            markDeleted()
        }
    }

    @CommandHandler
    fun doSomethingIllegal(command: IllegalStateChangeCommand) {
        apply(MyEvent(command.aggregateIdentifier, lastNumber!! + 1))
        lastNumber = command.newIllegalValue
    }

    @EventSourcingHandler
    fun handleMyEvent(event: MyEvent) {
        identifier = if (event.aggregateIdentifier == null) null else event.aggregateIdentifier.toString()
        lastNumber = event.someValue
        if (entity == null) {
            entity = MyEntity()
        }
    }

    @EventSourcingHandler
    fun deleted(event: MyAggregateDeletedEvent) {
        if (!event.isWithIllegalStateChange) {
            markDeleted()
        }
    }

    @EventSourcingHandler
    fun handleAll(event: DomainEventMessage<*>) {
        println("Invoked with payload: " + event.payloadType.name)
        // we don't care about events
    }

    override fun doSomething(command: TestCommand) {
        // this state change should be accepted, since it happens on a transient value
        counter++
        apply(MyEvent(command.aggregateIdentifier, lastNumber!! + 1))
    }
}
