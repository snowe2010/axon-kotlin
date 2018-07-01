package com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.dsl

import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.AggregateTestFixture
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.commandhandling.NoHandlerForCommandException
import org.axonframework.commandhandling.TargetAggregateIdentifier
import org.axonframework.commandhandling.model.AggregateIdentifier
import org.axonframework.commandhandling.model.AggregateLifecycle.apply
import org.axonframework.eventhandling.EventHandler
import org.axonframework.eventsourcing.eventstore.EventStoreException
import org.axonframework.test.FixtureExecutionException
import org.axonframework.test.aggregate.AggregateTestFixture
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class FixtureTest_ExceptionHandling {

    private val fixture = AggregateTestFixture<MyAggregate>()

    @Test
    fun testCreateAggregate() {
        fixture {
            given {
                commands { }
            }
            whenever {
                CreateMyAggregateCommand("14")
            }
            expect {
                events {
                    +MyAggregateCreatedEvent("14")
                }
            }
        }
    }

    @Test
    fun givenUnknownCommand() {
        assertFailsWith<NoHandlerForCommandException> {
            fixture {
                given {
                    commands {
                        +CreateMyAggregateCommand("14")
                        +UnknownCommand("14")
                    }
                }
            }
        }
    }

    @Test
    fun testWhenExceptionTriggeringCommand() {
        fixture {
            given {
                commands { +CreateMyAggregateCommand("14") }
            }
            whenever { ExceptionTriggeringCommand("14") }
            expect {
                exception<RuntimeException>()
            }
        }
    }

    @Test
    fun testGivenExceptionTriggeringCommand() {
        assertFailsWith<RuntimeException> {
            fixture {
                given {
                    commands {
                        +CreateMyAggregateCommand("14")
                        +ExceptionTriggeringCommand("14")
                    }
                }
            }
        }
    }

    @Test
    fun testGivenCommandWithInvalidIdentifier() {
        fixture {
            given {
                commands { +CreateMyAggregateCommand("1") }
            }
            whenever { ValidMyAggregateCommand("2") }
            expect {
                exception<EventStoreException>()
            }
        }
    }

    /** TODO 3.3 tests
    @Test
    fun testExceptionMessageCheck() {
    fixture.givenCommands(
    CreateMyAggregateCommand("1")
    ).whenever(
    ValidMyAggregateCommand("2")
    ).expectException(EventStoreException::class.java)
    .expectExceptionMessage("You probably want to use aggregateIdentifier() on your fixture to get the aggregate identifier to use")
    }

    @Test
    fun testExceptionMessageCheckWithMatcher() {
    fixture.givenCommands(
    CreateMyAggregateCommand("1")
    ).whenever(
    ValidMyAggregateCommand("2")
    ).expectException(EventStoreException::class.java)
    .expectExceptionMessage(containsString("You"))
    }
     */

    @Test
    fun testWhenCommandWithInvalidIdentifier() {
        assertFailsWith<FixtureExecutionException> {
            fixture {
                given {
                    commands {
                        +CreateMyAggregateCommand("1")
                        +ValidMyAggregateCommand("2")
                    }
                }
            }
        }
    }

    internal abstract class AbstractMyAggregateCommand protected constructor(@field:TargetAggregateIdentifier val id: String)

    internal class CreateMyAggregateCommand(id: String) : AbstractMyAggregateCommand(id)

    internal class ExceptionTriggeringCommand(id: String) : AbstractMyAggregateCommand(id)

    internal class ValidMyAggregateCommand(id: String) : AbstractMyAggregateCommand(id)

    internal class UnknownCommand(id: String) : AbstractMyAggregateCommand(id)

    internal class MyAggregateCreatedEvent(val id: String)

    internal class MyAggregate {
        @AggregateIdentifier
        private lateinit var id: String

        private constructor()

        @CommandHandler
        constructor(cmd: CreateMyAggregateCommand) {
            apply(MyAggregateCreatedEvent(cmd.id))
        }

        @CommandHandler
        fun handle(cmd: ValidMyAggregateCommand) {
            /* no-op */
        }

        @CommandHandler
        fun handle(cmd: ExceptionTriggeringCommand) {
            throw RuntimeException("Error")
        }

        @EventHandler
        private fun on(event: MyAggregateCreatedEvent) {
            this.id = event.id
        }
    }
}