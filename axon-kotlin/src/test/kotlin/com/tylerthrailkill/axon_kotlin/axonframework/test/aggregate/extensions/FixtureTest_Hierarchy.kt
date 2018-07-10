package com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions

import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.AggregateTestFixture
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.whenever
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.commandhandling.model.AggregateIdentifier
import org.axonframework.eventsourcing.AggregateFactory
import org.axonframework.eventsourcing.DomainEventMessage
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.commandhandling.model.AggregateLifecycle.apply
import org.junit.jupiter.api.Test


/**
 * @author Allard Buijze
 */
class FixtureTest_Hierarchy {

    @Test
    fun testFixtureSetupWithAggregateHierarchy() {
        AggregateTestFixture<AbstractAggregate>()
                .registerAggregateFactory(object : AggregateFactory<AbstractAggregate> {
                    override fun createAggregateRoot(aggregateIdentifier: String, firstEvent: DomainEventMessage<*>): AbstractAggregate {
                        return ConcreteAggregate()
                    }

                    override fun getAggregateType(): Class<AbstractAggregate> {
                        return AbstractAggregate::class.java
                    }
                })
                .given(MyEvent("123", 0)).whenever(TestCommand("123"))
                .expectEvents(MyEvent("123", 1))
    }

    internal abstract class AbstractAggregate {

        @AggregateIdentifier
        private var id: String? = null

        @CommandHandler
        abstract fun handle(testCommand: TestCommand)

        @EventSourcingHandler
        protected fun on(event: MyEvent) {
            this.id = event.aggregateIdentifier.toString()
        }
    }

    internal class ConcreteAggregate : AbstractAggregate() {

        override fun handle(testCommand: TestCommand) {
            apply(MyEvent(testCommand.aggregateIdentifier, 1))
        }
    }
}
