package com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions

import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.CreateAggregateCommand
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.commandhandling.TargetAggregateIdentifier
import org.axonframework.commandhandling.model.AggregateIdentifier
import org.axonframework.commandhandling.model.AggregateLifecycle.markDeleted
import org.axonframework.eventsourcing.AbstractAggregateFactory
import org.axonframework.eventsourcing.DomainEventMessage
import org.axonframework.eventsourcing.EventSourcingHandler
import java.util.*

import org.axonframework.commandhandling.model.AggregateLifecycle.apply
import org.axonframework.commandhandling.model.Repository
import org.axonframework.eventhandling.EventBus
import org.axonframework.eventhandling.GenericEventMessage
import org.hamcrest.BaseMatcher
import org.hamcrest.Description


/**
 * Heavily borrowed from AxonFramework will permission from Allard Buijze and Steven Van Beelen
 */
internal class StandardAggregate {

    @Transient
    private var counter: Int = 0
    private var lastNumber: Int? = null
    @AggregateIdentifier
    private lateinit var identifier: String
    private var entity: MyEntity? = null

    constructor(aggregateIdentifier: Any) {
        identifier = aggregateIdentifier.toString()
    }

    constructor(initialValue: Int, aggregateIdentifier: Any?) {
        apply(MyEvent(aggregateIdentifier ?: UUID.randomUUID(), initialValue))
    }

    fun delete(withIllegalStateChange: Boolean) {
        apply(MyAggregateDeletedEvent(withIllegalStateChange))
        if (withIllegalStateChange) {
            markDeleted()
        }
    }

    fun doSomethingIllegal(newIllegalValue: Int?) {
        apply(MyEvent(identifier, lastNumber!! + 1))
        lastNumber = newIllegalValue
    }

    @EventSourcingHandler
    fun handleMyEvent(event: MyEvent) {
        identifier = event.aggregateIdentifier.toString()
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
        // we don't care about events
    }

    fun doSomething() {
        // this state change should be accepted, since it happens on a transient value
        counter++
        apply(MyEvent(identifier, lastNumber!! + 1))
    }

    override fun hashCode(): Int {
        // This hashCode implementation is EVIL! But it's on purpose, because it shouldn't matter for Axon.
        var result = counter
        result = 31 * result + if (lastNumber != null) lastNumber!!.hashCode() else 0
        result = 31 * result + if (identifier != null) identifier!!.hashCode() else 0
        result = 31 * result + if (entity != null) entity!!.hashCode() else 0
        return result
    }

    internal class Factory : AbstractAggregateFactory<StandardAggregate>(StandardAggregate::class.java) {

        override fun doCreateAggregate(aggregateIdentifier: String, firstEvent: DomainEventMessage<*>): StandardAggregate {
            return StandardAggregate(aggregateIdentifier)
        }
    }
}


internal class MyEntity {
    var lastNumber: Int? = null
        private set

    @EventSourcingHandler
    fun handleMyEvent(event: MyEvent) {
        lastNumber = event.someValue
    }
}


internal class MyEvent(val aggregateIdentifier: Any?, val someValue: Int?, val someBytes: ByteArray = byteArrayOf())
internal class MyAggregateDeletedEvent(val isWithIllegalStateChange: Boolean)


internal data class TestCommand(@field:TargetAggregateIdentifier val aggregateIdentifier: Any)
internal class StrangeCommand(val aggregateIdentifier: Any)
internal class MyApplicationEvent
internal class PublishEventCommand(val aggregateIdentifier: Any)
class IllegalStateChangeCommand(val aggregateIdentifier: Any, val newIllegalValue: Int?)
class DeleteCommand(@field:TargetAggregateIdentifier val aggregateIdentifier: Any, val isAsIllegalChange: Boolean)

internal class StrangeCommandReceivedException(message: String) : RuntimeException(message) {
    companion object {
        private val serialVersionUID = -486498386422064414L
    }
}


internal class MyCommandHandler(var repository: Repository<StandardAggregate>, val eventBus: EventBus) {


    @CommandHandler
    @Throws(Exception::class)
    fun createAggregate(command: CreateAggregateCommand) {
        repository.newInstance { StandardAggregate(0, command.id) }
    }

    @CommandHandler
    fun handleTestCommand(testCommand: TestCommand) {
        repository.load(testCommand.aggregateIdentifier.toString(), null)
                .execute(StandardAggregate::doSomething)
    }

    @CommandHandler
    fun handleStrangeCommand(testCommand: StrangeCommand) {
        repository.load(testCommand.aggregateIdentifier.toString(), null).execute(StandardAggregate::doSomething)
        eventBus.publish(GenericEventMessage<Any>(MyApplicationEvent()))
        throw StrangeCommandReceivedException("Strange command received")
    }

    @CommandHandler
    fun handleEventPublishingCommand(testCommand: PublishEventCommand) {
        eventBus.publish(GenericEventMessage<Any>(MyApplicationEvent()))
    }

    @CommandHandler
    fun handleIllegalStateChange(command: IllegalStateChangeCommand) {
        val aggregate = repository.load(command.aggregateIdentifier.toString())
        aggregate.execute { r -> r.doSomethingIllegal(command.newIllegalValue) }
    }

    @CommandHandler
    fun handleDeleteAggregate(command: DeleteCommand) {
        repository.load(command.aggregateIdentifier.toString()).execute { r -> r.delete(command.isAsIllegalChange) }
    }
}


internal class DoesMatch<T> : BaseMatcher<T>() {

    override fun matches(o: Any?) = true

    override fun describeTo(description: Description) {
        description.appendText("anything")
    }
}

internal class DoesNotMatch<T> : BaseMatcher<T>() {

    override fun matches(o: Any?) = false

    override fun describeTo(description: Description) {
        description.appendText("something you can never give me")
    }
}