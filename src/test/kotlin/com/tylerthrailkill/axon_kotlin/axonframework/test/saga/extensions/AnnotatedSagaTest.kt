package com.tylerthrailkill.axon_kotlin.axonframework.test.saga.extensions


import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.isA
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import com.tylerthrailkill.axon_kotlin.axonframework.test.saga.SagaTestFixture
import org.axonframework.eventhandling.GenericEventMessage
import org.axonframework.messaging.Message
import org.axonframework.messaging.MetaData
import org.axonframework.test.matchers.Matchers
import org.axonframework.test.matchers.Matchers.listWithAnyOf
import org.axonframework.test.matchers.Matchers.messageWithPayload
import org.axonframework.test.matchers.Matchers.noEvents
import org.axonframework.test.utils.CallbackBehavior
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.any
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * @author Allard Buijze
 */
class AnnotatedSagaTest {

    @Test
    fun testFixtureApi_WhenEventOccurs() {
        val aggregate1 = UUID.randomUUID().toString()
        val aggregate2 = UUID.randomUUID().toString()
        val fixture = SagaTestFixture<StubSaga>()
        val validator = fixture
                .givenAggregate(aggregate1).published(
                        GenericEventMessage.asEventMessage<TriggerSagaStartEvent>(TriggerSagaStartEvent(aggregate1)), TriggerExistingSagaEvent(aggregate1))
                .andThenAggregate(aggregate2).published(TriggerSagaStartEvent(aggregate2))
                .whenAggregate(aggregate1).publishes(TriggerSagaEndEvent(aggregate1))

        validator.expectActiveSagas(1)
        validator.expectAssociationWith("identifier", aggregate2)
        validator.expectNoAssociationWith("identifier", aggregate1)
        validator.expectScheduledEventOfType(Duration.ofMinutes(10), TimerTriggeredEvent::class.java)
        validator.expectScheduledEventMatching(Duration.ofMinutes(10), messageWithPayload(CoreMatchers.any(
                TimerTriggeredEvent::class.java)))
        validator.expectScheduledEvent(Duration.ofMinutes(10), TimerTriggeredEvent(aggregate1))
        validator.expectScheduledEventOfType(fixture.currentTime().plusSeconds(600), TimerTriggeredEvent::class.java)
        validator.expectScheduledEventMatching(fixture.currentTime().plusSeconds(600),
                messageWithPayload(CoreMatchers.any(TimerTriggeredEvent::class.java)))
        validator.expectScheduledEvent(fixture.currentTime().plusSeconds(600),
                TimerTriggeredEvent(aggregate1))
        validator.expectDispatchedCommands()
        validator.expectNoDispatchedCommands()
        validator.expectPublishedEventsMatching(noEvents())
    }

    @Test
    fun testFixtureApi_AggregatePublishedEvent_NoHistoricActivity() {
        val fixture = SagaTestFixture<StubSaga>()
        fixture.givenNoPriorActivity()
                .whenAggregate("id").publishes(TriggerSagaStartEvent("id"))
                .expectActiveSagas(1)
                .expectAssociationWith("identifier", "id")
    }

    @Test
    fun testFixtureApi_NonTransientResourceInjected() {
        val fixture = SagaTestFixture<StubSaga>()
        fixture.registerResource(NonTransientResource())
        fixture.givenNoPriorActivity()
        try {
            fixture.whenAggregate("id").publishes(TriggerSagaStartEvent("id"))
            fail("Expected error")
        } catch (e: AssertionError) {
            assertTrue(e.message!!.contains("StubSaga.nonTransientResource"), "Got unexpected error: " + e.message)
            assertTrue(e.message!!.contains("transient"), "Got unexpected error: " + e.message)
        }

    }

    @Test
    fun testFixtureApi_NonTransientResourceInjected_CheckDisabled() {
        val fixture = SagaTestFixture<StubSaga>()
                .withTransienceCheckDisabled()
        fixture.registerResource(NonTransientResource())
        fixture.givenNoPriorActivity()
                .whenAggregate("id").publishes(TriggerSagaStartEvent("id"))
    }

    @Test // testing issue AXON-279
    fun testFixtureApi_PublishedEvent_NoHistoricActivity() {
        val fixture = SagaTestFixture<StubSaga>()
        fixture.givenNoPriorActivity()
                .whenPublishingA(GenericEventMessage<Any>(TriggerSagaStartEvent("id")))
                .expectActiveSagas(1)
                .expectAssociationWith("identifier", "id")
    }

    @Test
    @Throws(Exception::class)
    fun testFixtureApi_WithApplicationEvents() {
        val aggregate1 = UUID.randomUUID().toString()
        val aggregate2 = UUID.randomUUID().toString()
        val fixture = SagaTestFixture<StubSaga>()
        fixture.givenAPublished(TimerTriggeredEvent(UUID.randomUUID().toString()))
                .andThenAPublished(TimerTriggeredEvent(UUID.randomUUID().toString()))

                .whenPublishingA(TimerTriggeredEvent(UUID.randomUUID().toString()))

                .expectActiveSagas(0)
                .expectNoAssociationWith("identifier", aggregate2)
                .expectNoAssociationWith("identifier", aggregate1)
                .expectNoScheduledEvents()
                .expectDispatchedCommands()
                .expectPublishedEvents()
    }

    @Test
    fun testFixtureApi_WhenEventIsPublishedToEventBus() {
        val aggregate1 = UUID.randomUUID().toString()
        val aggregate2 = UUID.randomUUID().toString()
        val fixture = SagaTestFixture<StubSaga>()
        val validator = fixture
                .givenAggregate(aggregate1).published(TriggerSagaStartEvent(aggregate1),
                        TriggerExistingSagaEvent(aggregate1))
                .whenAggregate(aggregate1).publishes(TriggerExistingSagaEvent(aggregate1))

        validator.expectActiveSagas(1)
        validator.expectAssociationWith("identifier", aggregate1)
        validator.expectNoAssociationWith("identifier", aggregate2)
        validator.expectScheduledEventMatching(Duration.ofMinutes(10),
                Matchers.messageWithPayload(CoreMatchers.any(Any::class.java)))
        validator.expectDispatchedCommands()
        validator.expectPublishedEventsMatching(listWithAnyOf<Message<*>>(messageWithPayload(any(SagaWasTriggeredEvent::class.java))))
    }

    @Test
    @Throws(Exception::class)
    fun testFixtureApi_ElapsedTimeBetweenEventsHasEffectOnScheduler() {
        val aggregate1 = UUID.randomUUID().toString()
        val fixture = SagaTestFixture<StubSaga>()
        val validator = fixture
                // event schedules a TriggerEvent after 10 minutes from t0
                .givenAggregate(aggregate1).published(TriggerSagaStartEvent(aggregate1))
                // time shifts to t0+5
                .andThenTimeElapses(Duration.ofMinutes(5))
                // reset event schedules a TriggerEvent after 10 minutes from t0+5
                .andThenAggregate(aggregate1).published(ResetTriggerEvent(aggregate1))
                // when time shifts to t0+10
                .whenTimeElapses(Duration.ofMinutes(6))

        validator.expectActiveSagas(1)
        validator.expectAssociationWith("identifier", aggregate1)
        // 6 minutes have passed since the 10minute timer was reset,
        // so expect the timer to be scheduled for 4 minutes (t0 + 15)
        validator.expectScheduledEventMatching(Duration.ofMinutes(4),
                Matchers.messageWithPayload(CoreMatchers.any(Any::class.java)))
        validator.expectNoDispatchedCommands()
        validator.expectPublishedEvents()
    }


    @Test
    fun testFixtureApi_WhenTimeElapses_UsingMockGateway() {
        val identifier = UUID.randomUUID().toString()
        val identifier2 = UUID.randomUUID().toString()
        val fixture = SagaTestFixture<StubSaga>()
        val gateway = mock<StubGateway>()
        fixture.registerCommandGateway(StubGateway::class.java, gateway)
        whenever(gateway.send(eq("Say hi!"))).thenReturn("Hi again!")

        fixture.givenAggregate(identifier).published(TriggerSagaStartEvent(identifier))
                .andThenAggregate(identifier2).published(TriggerExistingSagaEvent(identifier2))
                .whenTimeElapses(Duration.ofMinutes(35))
                .expectActiveSagas(1)
                .expectAssociationWith("identifier", identifier)
                .expectNoAssociationWith("identifier", identifier2)
                .expectNoScheduledEvents()
                .expectDispatchedCommands("Say hi!", "Hi again!")
                .expectPublishedEventsMatching(noEvents())

        verify(gateway).send("Say hi!")
        verify(gateway).send("Hi again!")
    }

    @Test
    fun testSchedulingEventsAsMessage() {
        val identifier = UUID.randomUUID()
        val fixture = SagaTestFixture<StubSaga>()
        fixture.registerCommandGateway(StubGateway::class.java)

        fixture.givenNoPriorActivity()
                // this will create a message with a timestamp from the real time. It should be converted to fixture-time
                .whenPublishingA(GenericEventMessage.asEventMessage<TriggerSagaStartEvent>(TriggerSagaStartEvent(identifier.toString())))
                .expectScheduledEventOfType(Duration.ofMinutes(10), TimerTriggeredEvent::class.java)
    }

    @Test
    fun testSchedulingEventsAsDomainEventMessage() {
        val identifier = UUID.randomUUID()
        val fixture = SagaTestFixture<StubSaga>()
        fixture.registerCommandGateway(StubGateway::class.java)

        fixture.givenNoPriorActivity()
                // this will create a message with a timestamp from the real time. It should be converted to fixture-time
                .whenAggregate(UUID.randomUUID().toString()).publishes(GenericEventMessage.asEventMessage<TriggerSagaStartEvent>(TriggerSagaStartEvent(identifier.toString())))
                .expectScheduledEventOfType(Duration.ofMinutes(10), TimerTriggeredEvent::class.java)
    }

    @Test
    fun testScheduledEventsInPastAsDomainEventMessage() {
        val identifier = UUID.randomUUID()
        val fixture = SagaTestFixture<StubSaga>()
        fixture.registerCommandGateway(StubGateway::class.java)

        fixture.givenAggregate(UUID.randomUUID().toString()).published(GenericEventMessage.asEventMessage<TriggerSagaStartEvent>(TriggerSagaStartEvent(identifier.toString())))
                .whenTimeElapses(Duration.ofMinutes(1))
                .expectScheduledEventOfType(Duration.ofMinutes(9), TimerTriggeredEvent::class.java)
    }

    @Test
    fun testScheduledEventsInPastAsEventMessage() {
        val identifier = UUID.randomUUID()
        val fixture = SagaTestFixture<StubSaga>()
        fixture.registerCommandGateway(StubGateway::class.java)

        fixture.givenAPublished(GenericEventMessage.asEventMessage<TriggerSagaStartEvent>(TriggerSagaStartEvent(identifier.toString())))
                .whenTimeElapses(Duration.ofMinutes(1))
                .expectScheduledEventOfType(Duration.ofMinutes(9), TimerTriggeredEvent::class.java)
    }

    @Test
    fun testFixtureApi_givenCurrentTime() {
        val identifier = UUID.randomUUID().toString()
        val fourDaysAgo = Instant.now().minus(4, ChronoUnit.DAYS)
        val fourDaysMinusTenMinutesAgo = fourDaysAgo.plus(10, ChronoUnit.MINUTES)

        val fixture = SagaTestFixture<StubSaga>()
        fixture
                .givenCurrentTime(fourDaysAgo)
                .whenPublishingA(TriggerSagaStartEvent(identifier))
                .expectScheduledEvent(fourDaysMinusTenMinutesAgo, TimerTriggeredEvent(identifier))
    }

    @Test
    fun testFixtureApi_WhenTimeElapses_UsingDefaults() {
        val identifier = UUID.randomUUID().toString()
        val identifier2 = UUID.randomUUID().toString()
        val fixture = SagaTestFixture<StubSaga>()
        fixture.registerCommandGateway(StubGateway::class.java)

        fixture.givenAggregate(identifier).published(TriggerSagaStartEvent(identifier))
                .andThenAggregate(identifier2).published(TriggerExistingSagaEvent(identifier2))
                .whenTimeElapses(Duration.ofMinutes(35))
                .expectActiveSagas(1)
                .expectAssociationWith("identifier", identifier)
                .expectNoAssociationWith("identifier", identifier2)
                .expectNoScheduledEvents()
                // since we return null for the command, the other is never sent...
                .expectDispatchedCommands("Say hi!")
                .expectPublishedEventsMatching(noEvents())
    }

    @Test
    @Throws(Exception::class)
    fun testFixtureApi_WhenTimeElapses_UsingCallbackBehavior() {
        val identifier = UUID.randomUUID().toString()
        val identifier2 = UUID.randomUUID().toString()
        val fixture = SagaTestFixture<StubSaga>()
        val commandHandler = mock<CallbackBehavior>()
        whenever(commandHandler.handle(eq("Say hi!"), isA())).thenReturn("Hi again!")
        fixture.setCallbackBehavior(commandHandler)
        fixture.registerCommandGateway(StubGateway::class.java)

        fixture.givenAggregate(identifier).published(TriggerSagaStartEvent(identifier))
                .andThenAggregate(identifier2).published(TriggerExistingSagaEvent(identifier2))
                .whenTimeElapses(Duration.ofMinutes(35))
                .expectActiveSagas(1)
                .expectAssociationWith("identifier", identifier)
                .expectNoAssociationWith("identifier", identifier2)
                .expectNoScheduledEvents()
                .expectDispatchedCommands("Say hi!", "Hi again!")
                .expectPublishedEventsMatching(noEvents())

        verify(commandHandler, times(2)).handle(isA(), eq(MetaData.emptyInstance()))
    }

    @Test
    fun testFixtureApi_WhenTimeAdvances() {
        val identifier = UUID.randomUUID().toString()
        val identifier2 = UUID.randomUUID().toString()
        val fixture = SagaTestFixture<StubSaga>()
        fixture.registerCommandGateway(StubGateway::class.java)
        fixture.givenAggregate(identifier).published(TriggerSagaStartEvent(identifier))
                .andThenAggregate(identifier2).published(TriggerExistingSagaEvent(identifier2))

                .whenTimeAdvancesTo(Instant.now().plus(Duration.ofDays(1)))

                .expectActiveSagas(1)
                .expectAssociationWith("identifier", identifier)
                .expectNoAssociationWith("identifier", identifier2)
                .expectNoScheduledEvents()
                .expectDispatchedCommands("Say hi!")
    }

    @Test
    fun testLastResourceEvaluatedFirst() {
        val identifier = UUID.randomUUID().toString()
        val identifier2 = UUID.randomUUID().toString()
        val fixture = SagaTestFixture<StubSaga>()
        fixture.registerCommandGateway(StubGateway::class.java)
        val mock = mock<StubGateway>()
        fixture.registerCommandGateway(StubGateway::class.java, mock)
        fixture.givenAggregate(identifier).published(TriggerSagaStartEvent(identifier))
                .andThenAggregate(identifier2).published(TriggerExistingSagaEvent(identifier2))

                .whenTimeAdvancesTo(Instant.now().plus(Duration.ofDays(1)))

                .expectActiveSagas(1)
                .expectAssociationWith("identifier", identifier)
                .expectNoAssociationWith("identifier", identifier2)
                .expectNoScheduledEvents()
                .expectDispatchedCommands("Say hi!")
        verify(mock).send(anyString())
    }
}
