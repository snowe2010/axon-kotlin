package com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions

import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.AggregateTestFixture
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.whenever
import org.axonframework.commandhandling.model.AggregateNotFoundException
import org.axonframework.test.AxonAssertionError
import org.axonframework.test.aggregate.FixtureConfiguration
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
        fixture.registerAnnotatedCommandHandler(MyCommandHandler(fixture.repository, fixture.eventBus))
                .given()
                .whenever(TestCommand(UUID.randomUUID()))
                .expectException(AggregateNotFoundException::class.java)
    }

    @Test
    fun testFirstFixture() {
        val validator = fixture
                .registerAnnotatedCommandHandler(MyCommandHandler(fixture.repository, fixture.eventBus))
                .given(MyEvent("aggregateId", 1))
                .whenever(TestCommand("aggregateId"))
        validator.expectReturnValue(null)
        validator.expectEvents(MyEvent("aggregateId", 2))
    }

    @Test
    fun testExpectEventsIgnoresFilteredField() {
        val validator = fixture
                .registerAnnotatedCommandHandler(MyCommandHandler(fixture.repository, fixture.eventBus))
                .registerFieldFilter { field -> field.name != "someBytes" }
                .given(MyEvent("aggregateId", 1))
                .whenever(TestCommand("aggregateId"))
        validator.expectReturnValue(null)
        validator.expectEvents(MyEvent("aggregateId", 2, "ignored".toByteArray()))
    }

    @Test
    fun testFixture_SetterInjection() {
        val commandHandler = MyCommandHandler()
        commandHandler.repository = fixture.repository
        fixture.registerAnnotatedCommandHandler(commandHandler)
                .given(MyEvent("aggregateId", 1), MyEvent("aggregateId", 2))
                .whenever(TestCommand("aggregateId"))
                .expectReturnValueMatching(IsNull.nullValue())
                .expectEvents(MyEvent("aggregateId", 3))
    }

    @Test
    fun testFixture_GivenAList() {
        val givenEvents = Arrays.asList<Any>(MyEvent("aggregateId", 1), MyEvent("aggregateId", 2),
                MyEvent("aggregateId", 3))
        fixture.registerAnnotatedCommandHandler(MyCommandHandler(fixture.repository, fixture.eventBus))
                .given(givenEvents)
                .whenever(TestCommand("aggregateId"))
                .expectEvents(MyEvent("aggregateId", 4))
                .expectSuccessfulHandlerExecution()
    }

    @Test
    fun testFixtureDetectsStateChangeOutsideOfHandler_ExplicitValue() {
        val givenEvents = Arrays.asList<Any>(MyEvent("aggregateId", 1), MyEvent("aggregateId", 2),
                MyEvent("aggregateId", 3))
        try {
            fixture.registerAnnotatedCommandHandler(MyCommandHandler(fixture.repository, fixture.eventBus))
                    .given(givenEvents)
                    .whenever(IllegalStateChangeCommand("aggregateId", 5))
            fail("Expected AssertionError")
        } catch (e: AssertionError) {
            assertTrue(e.message!!.contains(".lastNumber\""), "Wrong message: " + e.message)
            assertTrue(e.message!!.contains("<5>"), "Wrong message: " + e.message)
            assertTrue(e.message!!.contains("<4>"), "Wrong message: " + e.message)
        }

    }

    @Test
    fun testFixtureIgnoredStateChangeInFilteredField() {
        val givenEvents = Arrays.asList<Any>(MyEvent("aggregateId", 1), MyEvent("aggregateId", 2),
                MyEvent("aggregateId", 3))
        fixture.registerFieldFilter { field -> field.name != "lastNumber" }
        fixture.registerAnnotatedCommandHandler(MyCommandHandler(fixture.repository, fixture.eventBus))
                .given(givenEvents)
                .whenever(IllegalStateChangeCommand("aggregateId", 5))
    }

    @Test
    fun testFixtureDetectsStateChangeOutsideOfHandler_NullValue() {
        val givenEvents = Arrays.asList<Any>(MyEvent("aggregateId", 1), MyEvent("aggregateId", 2),
                MyEvent("aggregateId", 3))
        try {
            fixture.registerAnnotatedCommandHandler(MyCommandHandler(fixture.repository, fixture.eventBus))
                    .given(givenEvents)
                    .whenever(IllegalStateChangeCommand("aggregateId", null))
            fail("Expected AssertionError")
        } catch (e: AssertionError) {
            assertTrue(e.message!!.contains(".lastNumber\""), "Wrong message: " + e.message)
            assertTrue(e.message!!.contains("<null>"), "Wrong message: " + e.message)
            assertTrue(e.message!!.contains("<4>"), "Wrong message: " + e.message)
        }

    }

    @Test
    fun testFixtureDetectsStateChangeOutsideOfHandler_Ignored() {
        val givenEvents = Arrays.asList<Any>(MyEvent("aggregateId", 1), MyEvent("aggregateId", 2),
                MyEvent("aggregateId", 3))
        fixture.setReportIllegalStateChange(false)
        fixture.registerAnnotatedCommandHandler(MyCommandHandler(fixture.repository, fixture.eventBus))
                .given(givenEvents)
                .whenever(IllegalStateChangeCommand("aggregateId", null))
    }

    @Test
    fun testFixtureDetectsStateChangeOutsideOfHandler_AggregateGeneratesIdentifier() {
        fixture.registerAnnotatedCommandHandler(MyCommandHandler(fixture.repository, fixture.eventBus))
                .given()
                .whenever(CreateAggregateCommand(null))
    }

    @Test
    fun testFixtureDetectsStateChangeOutsideOfHandler_AggregateDeleted() {
        val exec = fixture.registerAnnotatedCommandHandler(MyCommandHandler(fixture.repository, fixture.eventBus))
                .given(MyEvent("aggregateId", 5))
        try {
            exec.whenever(DeleteCommand("aggregateId", true))
            fail("Fixture should have failed")
        } catch (error: AssertionError) {
            assertTrue(error.message!!.contains("considered deleted"), "Wrong message: " + error.message)
        }

    }

    @Test
    fun testFixture_AggregateDeleted() {
        fixture.registerAnnotatedCommandHandler(MyCommandHandler(fixture.repository, fixture.eventBus))
                .given(MyEvent("aggregateId", 5))
                .whenever(DeleteCommand("aggregateId", false))
                .expectEvents(MyAggregateDeletedEvent(false))
    }

    @Test
    fun testFixtureGivenCommands() {
        fixture.registerAnnotatedCommandHandler(MyCommandHandler(fixture.repository, fixture.eventBus))
                .givenCommands(CreateAggregateCommand("aggregateId"),
                        TestCommand("aggregateId"),
                        TestCommand("aggregateId"),
                        TestCommand("aggregateId"))
                .whenever(TestCommand("aggregateId"))
                .expectEvents(MyEvent("aggregateId", 4))
    }

    @Test
    fun testFixture_CommandHandlerDispatchesNonDomainEvents() {
        val givenEvents = Arrays.asList<Any>(MyEvent("aggregateId", 1), MyEvent("aggregateId", 2),
                MyEvent("aggregateId", 3))
        val commandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
        // the domain events are part of the transaction, but the command handler directly dispatches an application
        // event to the event bus. This event dispatched anyway. The
        fixture.registerAnnotatedCommandHandler(commandHandler)
                .given(givenEvents)
                .whenever(PublishEventCommand("aggregateId"))
                .expectEvents(MyApplicationEvent())
    }

    @Test
    fun testFixture_ReportWrongNumberOfEvents() {
        val givenEvents = Arrays.asList<Any>(MyEvent("aggregateId", 1), MyEvent("aggregateId", 2),
                MyEvent("aggregateId", 3))
        val commandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
        try {
            fixture.registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .whenever(TestCommand("aggregateId"))
                    .expectEvents(MyEvent("aggregateId", 4), MyEvent("aggregateId", 5))
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("org.axonframework.test.aggregate.MyEvent <|> "))
        }

    }

    @Test
    fun testFixture_ReportWrongEvents() {
        val givenEvents = Arrays.asList<Any>(MyEvent("aggregateId", 1), MyEvent("aggregateId", 2),
                MyEvent("aggregateId", 3))
        val commandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
        try {
            fixture.registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .whenever(TestCommand("aggregateId"))
                    .expectEvents(MyOtherEvent())
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("org.axonframework.test.aggregate.MyOtherEvent <|>" + " org.axonframework.test.aggregate.MyEvent"))
        }

    }

    @Test
    fun testFixture_UnexpectedException() {
        val givenEvents = Arrays.asList<Any>(MyEvent("aggregateId", 1), MyEvent("aggregateId", 2),
                MyEvent("aggregateId", 3))
        val commandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
        try {
            fixture.registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .whenever(StrangeCommand("aggregateId"))
                    .expectSuccessfulHandlerExecution()
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("but got <exception of type [StrangeCommandReceivedException]>"))
        }

    }

    @Test
    fun testFixture_UnexpectedReturnValue() {
        val givenEvents = Arrays.asList<Any>(MyEvent("aggregateId", 1), MyEvent("aggregateId", 2),
                MyEvent("aggregateId", 3))
        val commandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
        try {
            fixture.registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .whenever(TestCommand("aggregateId"))
                    .expectException(RuntimeException::class.java)
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("The command handler returned normally, but an exception was expected"))
            assertTrue(e.message!!.contains(
                    "<an instance of java.lang.RuntimeException> but returned with <null>"))
        }

    }

    @Test
    fun testFixture_WrongReturnValue() {
        val givenEvents = Arrays.asList<Any>(MyEvent("aggregateId", 1), MyEvent("aggregateId", 2),
                MyEvent("aggregateId", 3))
        val commandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
        try {
            fixture.registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .whenever(TestCommand("aggregateId"))
                    .expectReturnValue("some")
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("<\"some\"> but got <null>"), e.message)
        }

    }

    @Test
    fun testFixture_WrongExceptionType() {
        val givenEvents = Arrays.asList<Any>(MyEvent("aggregateId", 1), MyEvent("aggregateId", 2),
                MyEvent("aggregateId", 3))
        val commandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
        try {
            fixture.registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .whenever(StrangeCommand("aggregateId"))
                    .expectException(IOException::class.java)
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains(
                    "<an instance of java.io.IOException> but got <exception of type [StrangeCommandReceivedException]>"))
        }

    }

    @Test
    fun testFixture_WrongEventContents() {
        val givenEvents = Arrays.asList<Any>(MyEvent("aggregateId", 1), MyEvent("aggregateId", 2),
                MyEvent("aggregateId", 3))
        val commandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
        try {
            fixture.registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .whenever(TestCommand("aggregateId"))
                    .expectEvents(MyEvent("aggregateId", 5)) // should be 4
                    .expectSuccessfulHandlerExecution()
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("In an event of type [MyEvent], the property [someValue] was not as expected."))
            assertTrue(e.message!!.contains("Expected <5> but got <4>"))
        }

    }

    @Test
    fun testFixture_WrongEventContents_WithNullValues() {
        val givenEvents = Arrays.asList<Any>(MyEvent("aggregateId", 1), MyEvent("aggregateId", 2),
                MyEvent("aggregateId", 3))
        val commandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
        try {
            fixture.registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .whenever(TestCommand("aggregateId"))
                    .expectEvents(MyEvent("aggregateId", null)) // should be 4
                    .expectSuccessfulHandlerExecution()
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains(
                    "In an event of type [MyEvent], the property [someValue] was not as expected."))
            assertTrue(e.message!!.contains("Expected <<null>> but got <4>"))
        }

    }

    @Test
    fun testFixture_ExpectedPublishedSameAsStored() {
        val givenEvents = Arrays.asList<Any>(MyEvent("aggregateId", 1), MyEvent("aggregateId", 2),
                MyEvent("aggregateId", 3))
        val commandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
        try {
            fixture.registerAnnotatedCommandHandler(commandHandler)
                    .given(givenEvents)
                    .whenever(StrangeCommand("aggregateId"))
                    .expectException(StrangeCommandReceivedException::class.java)
                    .expectEvents(MyEvent("aggregateId", 4)) // should be 4
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("The published events do not match the expected events"))
            assertTrue(e.message!!.contains("org.axonframework.test.aggregate.MyEvent <|> "))
            assertTrue(e.message!!.contains("probable cause"))
        }

    }
}
