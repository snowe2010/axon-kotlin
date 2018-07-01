package com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.dsl

import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.AggregateTestFixture
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions.CreateAggregateCommand
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions.DeleteCommand
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions.IllegalStateChangeCommand
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions.MyAggregateDeletedEvent
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions.MyApplicationEvent
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions.MyCommandHandler
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions.MyEvent
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions.MyOtherEvent
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions.PublishEventCommand
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions.StandardAggregate
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions.StrangeCommand
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions.StrangeCommandReceivedException
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions.TestCommand
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.whenever
import org.axonframework.commandhandling.model.AggregateNotFoundException
import org.axonframework.test.AxonAssertionError
import org.axonframework.test.aggregate.FixtureConfiguration
import org.axonframework.test.matchers.FieldFilter
import org.hamcrest.core.IsNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.*
import kotlin.test.assertTrue
import kotlin.test.fail


/**
 * @author Allard Buijze
 * @since 0.7
 */
class FixtureTest_RegularParams {

    private lateinit var fixture: FixtureConfiguration<StandardAggregate>

    @BeforeEach
    fun setUp() {
        fixture = AggregateTestFixture<StandardAggregate>()
        fixture.registerAggregateFactory(StandardAggregate.Factory())
    }

    @Test
    fun testFixture_NoEventsInStore() {
        fixture {
            register {
                annotatedCommandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
            }
            whenever { TestCommand(UUID.randomUUID()) }
            expect {
                exception<AggregateNotFoundException>()
            }
        }
    }

    @Test
    fun testFirstFixture() {
        val validator = fixture {
            register {
                annotatedCommandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
            }
            given {
                events { +MyEvent("aggregateId", 1) }
            }
            whenever { TestCommand("aggregateId") }
        }.resultValidator
        validator.expectReturnValue(null)
        validator.expectEvents(MyEvent("aggregateId", 2))
    }

    @Test
    fun testExpectEventsIgnoresFilteredField() {
        val validator = fixture {
            register {
                annotatedCommandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
                fieldFilters {
                    +FieldFilter { field -> field.name != "someBytes" }
                }
            }
            given {
                events {
                    +MyEvent("aggregateId", 1)
                }
            }
            whenever { TestCommand("aggregateId") }
        }.resultValidator
//        val validator = fixture
//                .registerAnnotatedCommandHandler(MyCommandHandler(fixture.repository, fixture.eventBus))
//                .registerFieldFilter { field -> field.name != "someBytes" }
//                .given(MyEvent("aggregateId", 1))
//                .whenever(TestCommand("aggregateId"))
        validator.expectReturnValue(null)
        validator.expectEvents(MyEvent("aggregateId", 2, "ignored".toByteArray()))
    }

    @Test
    fun testFixture_SetterInjection() {
        val commandHandler = MyCommandHandler()
        commandHandler.repository = fixture.repository
        fixture {
            register {
                annotatedCommandHandler = commandHandler
            }
            given {
                events {
                    +MyEvent("aggregateId", 1)
                    +MyEvent("aggregateId", 2)
                }
            }
            whenever { TestCommand("aggregateId") }
            expect {
                returnValueMatching = IsNull.nullValue()
                events {
                    +MyEvent("aggregateId", 3)
                }
            }
        }
    }

    @Test
    fun testFixtureDetectsStateChangeOutsideOfHandler_ExplicitValue() {
        try {
            fixture {
                register {
                    annotatedCommandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
                }
                given {
                    events {
                        +MyEvent("aggregateId", 1)
                        +MyEvent("aggregateId", 2)
                        +MyEvent("aggregateId", 3)
                    }
                }
                whenever { IllegalStateChangeCommand("aggregateId", 5) }
            }
            fail("Expected AssertionError")
        } catch (e: AssertionError) {
            assertTrue(e.message!!.contains(".lastNumber\""), "Wrong message: " + e.message)
            assertTrue(e.message!!.contains("<5>"), "Wrong message: " + e.message)
            assertTrue(e.message!!.contains("<4>"), "Wrong message: " + e.message)
        }

    }

    @Test
    fun testFixtureIgnoredStateChangeInFilteredField() {
        fixture {
            register {
                fieldFilters {
                    +FieldFilter { field -> field.name != "lastNumber" }
                }
                annotatedCommandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
            }
            given {
                events {
                    +MyEvent("aggregateId", 1)
                    +MyEvent("aggregateId", 2)
                    +MyEvent("aggregateId", 3)
                }
            }
            whenever { IllegalStateChangeCommand("aggregateId", 5) }
        }
    }

    @Test
    fun testFixtureDetectsStateChangeOutsideOfHandler_NullValue() {
        try {
            fixture {
                register {
                    annotatedCommandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
                }
                given {
                    events {
                        +MyEvent("aggregateId", 1)
                        +MyEvent("aggregateId", 2)
                        +MyEvent("aggregateId", 3)
                    }
                }
                whenever { IllegalStateChangeCommand("aggregateId", null) }
            }
            fail("Expected AssertionError")
        } catch (e: AssertionError) {
            assertTrue(e.message!!.contains(".lastNumber\""), "Wrong message: " + e.message)
            assertTrue(e.message!!.contains("<null>"), "Wrong message: " + e.message)
            assertTrue(e.message!!.contains("<4>"), "Wrong message: " + e.message)
        }

    }

    @Test
    fun testFixtureDetectsStateChangeOutsideOfHandler_Ignored() {
        fixture {
            reportIllegalStateChange = false
            register {
                annotatedCommandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
            }
            given {
                events {
                    +MyEvent("aggregateId", 1)
                    +MyEvent("aggregateId", 2)
                    +MyEvent("aggregateId", 3)
                }
            }
            whenever { IllegalStateChangeCommand("aggregateId", null) }
        }
    }

    @Test
    fun testFixtureDetectsStateChangeOutsideOfHandler_AggregateGeneratesIdentifier() {
        fixture {
            register {
                annotatedCommandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
            }
            whenever { CreateAggregateCommand(null) }
        }
    }

    @Test
    fun testFixtureDetectsStateChangeOutsideOfHandler_AggregateDeleted() {
        val exec = fixture {
            register {
                annotatedCommandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
            }
            given {
                events {
                    +MyEvent("aggregateId", 5)
                }
            }
        }.testExecutor
        try {
            // TODO, add invoke for TestExecutor, so you can continue to use dsl if you save state.
            exec.whenever(DeleteCommand("aggregateId", true))
            fail("Fixture should have failed")
        } catch (error: AssertionError) {
            assertTrue(error.message!!.contains("considered deleted"), "Wrong message: " + error.message)
        }

    }

    @Test
    fun testFixture_AggregateDeleted() {
        fixture {
            register {
                annotatedCommandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
            }
            given {
                events {
                    +MyEvent("aggregateId", 5)
                }
            }
            whenever { DeleteCommand("aggregateId", false) }
            expect {
                events { +MyAggregateDeletedEvent(false) }
            }
        }
    }

    @Test
    fun testFixtureGivenCommands() {
        fixture {
            register { annotatedCommandHandler = MyCommandHandler(fixture.repository, fixture.eventBus) }
            given {
                commands {
                    +CreateAggregateCommand("aggregateId")
                    +TestCommand("aggregateId")
                    +TestCommand("aggregateId")
                    +TestCommand("aggregateId")
                }
                whenever { TestCommand("aggregateId") }
                expect {
                    events { +MyEvent("aggregateId", 4) }
                }
            }
        }
    }

    @Test
    fun testFixture_CommandHandlerDispatchesNonDomainEvents() {
        val commandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
        // the domain events are part of the transaction, but the command handler directly dispatches an application
        // event to the event bus. This event dispatched anyway. The
        fixture {
            register {
                annotatedCommandHandler = commandHandler
            }
            given {
                events {
                    +MyEvent("aggregateId", 1)
                    +MyEvent("aggregateId", 2)
                    +MyEvent("aggregateId", 3)
                }
            }
            whenever { PublishEventCommand("aggregateId") }
            expect {
                events { +MyApplicationEvent() }
            }
        }
    }

    @Test
    fun testFixture_ReportWrongNumberOfEvents() {
        val commandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
        try {
            fixture {
                register {
                    annotatedCommandHandler = commandHandler
                }
                given {
                    events {
                        +MyEvent("aggregateId", 1)
                        +MyEvent("aggregateId", 2)
                        +MyEvent("aggregateId", 3)
                    }
                }
                whenever { TestCommand("aggregateId") }
                expect {
                    events {
                        +MyEvent("aggregateId", 4)
                        +MyEvent("aggregateId", 5)
                    }
                }
            }
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions.MyEvent <|> "))
        }

    }

    @Test
    fun testFixture_ReportWrongEvents() {
        val commandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
        try {
            fixture {
                register {
                    annotatedCommandHandler = commandHandler
                }
                given {
                    events {
                        +MyEvent("aggregateId", 1)
                        +MyEvent("aggregateId", 2)
                        +MyEvent("aggregateId", 3)
                    }
                }
                whenever { TestCommand("aggregateId") }
                expect {
                    events { +MyOtherEvent() }
                }
            }
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions.MyOtherEvent <|> com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions.MyEvent"))
        }

    }

    @Test
    fun testFixture_UnexpectedException() {
        val commandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
        try {
            fixture {
                register {
                    annotatedCommandHandler = commandHandler
                }
                given {
                    events {
                        +MyEvent("aggregateId", 1)
                        +MyEvent("aggregateId", 2)
                        +MyEvent("aggregateId", 3)
                    }
                }
                whenever { StrangeCommand("aggregateId") }
                expect { successfulHandlerExecution() }
            }
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("but got <exception of type [StrangeCommandReceivedException]>"))
        }

    }

    @Test
    fun testFixture_UnexpectedReturnValue() {
        val commandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
        try {
            fixture {
                register { annotatedCommandHandler = commandHandler }
                given {
                    events {
                        +MyEvent("aggregateId", 1)
                        +MyEvent("aggregateId", 2)
                        +MyEvent("aggregateId", 3)
                    }
                }
                whenever { TestCommand("aggregateId") }
                expect {
                    exception<RuntimeException>()
                }
            }
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("The command handler returned normally, but an exception was expected"))
            assertTrue(e.message!!.contains("<an instance of java.lang.RuntimeException> but returned with <null>"))
        }

    }

    @Test
    fun testFixture_WrongReturnValue() {
        val commandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
        try {
            fixture {
                register { annotatedCommandHandler = commandHandler }
                given {
                    events {
                        +MyEvent("aggregateId", 1)
                        +MyEvent("aggregateId", 2)
                        +MyEvent("aggregateId", 3)
                    }
                }
                whenever { TestCommand("aggregateId") }
                expect { returnValue = "some" }
            }
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("<\"some\"> but got <null>"), e.message)
        }

    }

    @Test
    fun testFixture_WrongExceptionType() {
        val commandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
        try {
            fixture {
                register { annotatedCommandHandler = commandHandler }
                given {
                    events {
                        +MyEvent("aggregateId", 1)
                        +MyEvent("aggregateId", 2)
                        +MyEvent("aggregateId", 3)
                    }
                }
                whenever { StrangeCommand("aggregateId") }
                expect { exception<IOException>() }
            }
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("<an instance of java.io.IOException> but got <exception of type [StrangeCommandReceivedException]>"))
        }

    }

    @Test
    fun testFixture_WrongEventContents() {
        val commandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
        try {
            fixture {
                register { annotatedCommandHandler = commandHandler }
                given {
                    events {
                        +MyEvent("aggregateId", 1)
                        +MyEvent("aggregateId", 2)
                        +MyEvent("aggregateId", 3)
                    }
                }
                whenever { TestCommand("aggregateId") }
                expect {
                    events { +MyEvent("aggregateId", 5) }
                    successfulHandlerExecution()
                }
            }
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("In an event of type [MyEvent], the property [someValue] was not as expected."))
            assertTrue(e.message!!.contains("Expected <5> but got <4>"))
        }

    }

    @Test
    fun testFixture_WrongEventContents_WithNullValues() {
        val commandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
        try {
            fixture {
                register { annotatedCommandHandler = commandHandler }
                given {
                    events {
                        +MyEvent("aggregateId", 1)
                        +MyEvent("aggregateId", 2)
                        +MyEvent("aggregateId", 3)
                    }

                }
                whenever { TestCommand("aggregateId") }
                expect {
                    events { +MyEvent("aggregateId", null) }
                    successfulHandlerExecution()
                }
            }
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("In an event of type [MyEvent], the property [someValue] was not as expected."))
            assertTrue(e.message!!.contains("Expected <<null>> but got <4>"))
        }

    }

    @Test
    fun testFixture_ExpectedPublishedSameAsStored() {
        val commandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
        try {
            fixture {
                register { annotatedCommandHandler = commandHandler }
                given {
                    events {
                        +MyEvent("aggregateId", 1)
                        +MyEvent("aggregateId", 2)
                        +MyEvent("aggregateId", 3)
                    }
                }
                whenever { StrangeCommand("aggregateId") }
                expect {
                    exception<StrangeCommandReceivedException>()
                    events { +MyEvent("aggregateId", 4) }
                }
            }
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("The published events do not match the expected events"))
            assertTrue(e.message!!.contains("com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions.MyEvent <|> "))
            assertTrue(e.message!!.contains("probable cause"))
        }

    }
}
