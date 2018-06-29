package com.tylerthrailkill.axon_kotlin.axonframework.test.saga.dsl

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.isA
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import com.tylerthrailkill.axon_kotlin.axonframework.test.saga.SagaTestFixture
import com.tylerthrailkill.axon_kotlin.axonframework.test.uuidString
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
import javax.rmi.CORBA.Stub
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * @author Allard Buijze
 * @author Tyler Thrailkill
 */
internal class AnnotatedSagaTest {

    @Test
    fun testFixtureApi_WhenEventOccurs() {
        val aggregate1 = uuidString()
        val aggregate2 = uuidString()
        val fixture = SagaTestFixture<StubSaga>()
        fixture {
            given {
                aggregate1 published {
                    +GenericEventMessage.asEventMessage<TriggerSagaStartEvent>(TriggerSagaStartEvent(aggregate1))
                    +TriggerExistingSagaEvent(aggregate1)
                }
                aggregate2 published TriggerSagaStartEvent(aggregate2)
            }
            whenever {
                aggregate1 publishes TriggerSagaEndEvent(aggregate1)
            }
            expect {
                activeSagas = 1
                "identifier" associatedWith aggregate2
                "identifier" notAssociatedWith aggregate1

                scheduledEvent(Duration.ofMinutes(10)).ofType<TimerTriggeredEvent>()
                scheduledEvent(Duration.ofMinutes(10)).matching(messageWithPayload(any(TimerTriggeredEvent::class.java)))
                scheduledEvent(Duration.ofMinutes(10)).of(TimerTriggeredEvent(aggregate1))

                scheduledEvent(fixture.currentTime().plusSeconds(600)).ofType<TimerTriggeredEvent>()
                scheduledEvent(fixture.currentTime().plusSeconds(600)).matching(messageWithPayload(CoreMatchers.any(TimerTriggeredEvent::class.java)))
                scheduledEvent(fixture.currentTime().plusSeconds(600)).of(TimerTriggeredEvent(aggregate1))
                dispatchedCommands {}
                noDispatchedCommands()
                publishedEventsMatching(noEvents())
            }
        }
    }

    @Test
    fun testFixtureApi_AggregatePublishedEvent_NoHistoricActivity() {
        val fixture = SagaTestFixture<StubSaga>()
        fixture {
            given {
                noPriorActivity()
            }
            whenever {
                "id" publishes TriggerSagaStartEvent("id")
            }
            expect {
                activeSagas = 1
                "identifier" associatedWith "id"
            }
        }
    }

    @Test
    fun testFixtureApi_NonTransientResourceInjected() {
        val fixture = SagaTestFixture<StubSaga>()
        try {
            fixture {
                register {
                    resources { +NonTransientResource() }
                }
                given { noPriorActivity() }
                whenever {
                    "id" publishes TriggerSagaStartEvent("id")
                }
            }
            fail("Expected error")
        } catch (e: AssertionError) {
            assertTrue(e.message!!.contains("StubSaga.nonTransientResource"), "Got unexpected error: " + e.message)
            assertTrue(e.message!!.contains("transient"), "Got unexpected error: " + e.message)
        }

    }

    @Test
    fun testFixtureApi_NonTransientResourceInjected_CheckDisabled() {
        val fixture = SagaTestFixture<StubSaga>()
        fixture {
            register {
                transienceCheckDisabled()
                resources { +NonTransientResource() }
            }
            given {
                noPriorActivity()
            }
            whenever {
                "id" publishes TriggerSagaStartEvent("id")
            }
        }
    }

    @Test // testing issue AXON-279
    fun testFixtureApi_PublishedEvent_NoHistoricActivity() {
        val fixture = SagaTestFixture<StubSaga>()
        fixture {
            given { noPriorActivity() }
            whenever {
                publishing(GenericEventMessage<Any>(TriggerSagaStartEvent("id")))
            }
            expect {
                activeSagas = 1
                "identifier" associatedWith "id"
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testFixtureApi_WithApplicationEvents() {
        val aggregate1 = UUID.randomUUID().toString()
        val aggregate2 = UUID.randomUUID().toString()
        val fixture = SagaTestFixture<StubSaga>()
        fixture {
            given {
                publishedInOrder {
                    +TimerTriggeredEvent(UUID.randomUUID().toString())
                    +TimerTriggeredEvent(UUID.randomUUID().toString())
                }
            }
            whenever {
                publishing(TimerTriggeredEvent(UUID.randomUUID().toString()))
            }
            expect {
                activeSagas = 0
                "identifier" notAssociatedWith aggregate2
                "identifier" notAssociatedWith aggregate1
                noScheduledEvents()
                dispatchedCommands { }
                publishedEvents { }
            }
        }
    }

    @Test
    fun testFixtureApi_WhenEventIsPublishedToEventBus() {
        val aggregate1 = UUID.randomUUID().toString()
        val aggregate2 = UUID.randomUUID().toString()
        val fixture = SagaTestFixture<StubSaga>()
        val validator = fixture {
            given {
                aggregate1 published {
                    +TriggerSagaStartEvent(aggregate1)
                    +TriggerExistingSagaEvent(aggregate1)
                }
            }
            whenever {
                aggregate1 publishes TriggerExistingSagaEvent(aggregate1)
            }
            expect {
                activeSagas = 1
                "identifier" associatedWith aggregate1
                "identifier" notAssociatedWith aggregate2
                scheduledEvent(Duration.ofMinutes(10)).matching(Matchers.messageWithPayload(CoreMatchers.any(Any::class.java)))
                dispatchedCommands { }
                publishedEventsMatching(listWithAnyOf<Message<*>>(messageWithPayload(any(SagaWasTriggeredEvent::class.java))))
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testFixtureApi_ElapsedTimeBetweenEventsHasEffectOnScheduler() {
        val aggregate1 = UUID.randomUUID().toString()
        val fixture = SagaTestFixture<StubSaga>()
        fixture {
            given {
                // event schedules a TriggerEvent after 10 minutes from t0
                aggregate1 published TriggerSagaStartEvent(aggregate1)
                // time shifts to t0+5
                thenTimeElapses(Duration.ofMinutes(5))
                // reset event schedules a TriggerEvent after 10 minutes from t0+5
                aggregate1 published ResetTriggerEvent(aggregate1)
            }
            whenever {
                // when time shifts to t0+10
                timeElapses(Duration.ofMinutes(6))
            }
            expect {
                activeSagas = 1
                "identifier" associatedWith aggregate1
                // 6 minutes have passed since the 10minute timer was reset,
                // so expect the timer to be scheduled for 4 minutes (t0 + 15)
                scheduledEvent(Duration.ofMinutes(4)).matching(Matchers.messageWithPayload(CoreMatchers.any(Any::class.java)))
                noDispatchedCommands()
                publishedEvents { }
            }
        }
    }


    @Test
    fun testFixtureApi_WhenTimeElapses_UsingMockGateway() {
        val identifier = UUID.randomUUID().toString()
        val identifier2 = UUID.randomUUID().toString()
        val fixture = SagaTestFixture<StubSaga>()
        val gateway = mock<StubGateway>()
        fixture {
            register {
                commandGateway(gateway)
            }
            whenever(gateway.send(eq("Say hi!"))).thenReturn("Hi again!")
            given {
                identifier published TriggerSagaStartEvent(identifier)
                identifier2 published TriggerExistingSagaEvent(identifier2)
            }
            whenever {
                timeElapses(Duration.ofMinutes(35))
            }
            expect {
                activeSagas = 1
                "identifier" associatedWith identifier
                "identifier" notAssociatedWith identifier2
                noScheduledEvents()
                dispatchedCommands {
                    +"Say hi!"
                    +"Hi again!"
                }
                publishedEventsMatching(noEvents())
            }
        }

        verify(gateway).send("Say hi!")
        verify(gateway).send("Hi again!")
    }

    @Test
    fun testSchedulingEventsAsMessage() {
        val identifier = UUID.randomUUID()
        val fixture = SagaTestFixture<StubSaga>()
        fixture {
            register {
                commandGateway<StubGateway>()
            }
            given {
                noPriorActivity()
            }
            whenever {
                // this will create a message with a timestamp from the real time. It should be converted to fixture-time
                publishing(GenericEventMessage.asEventMessage<TriggerSagaStartEvent>(TriggerSagaStartEvent(identifier.toString())))
            }
            expect {
                scheduledEvent(Duration.ofMinutes(10)).ofType<TimerTriggeredEvent>()
            }
        }
//        fixture.registerCommandGateway(StubGateway::class.java)

//        fixture.givenNoPriorActivity()
//                .whenPublishingA(GenericEventMessage.asEventMessage<TriggerSagaStartEvent>(TriggerSagaStartEvent(identifier.toString())))
//                .expectScheduledEventOfType(Duration.ofMinutes(10), TimerTriggeredEvent::class.java)
    }

    @Test
    fun testSchedulingEventsAsDomainEventMessage() {
        val identifier = UUID.randomUUID()
        val fixture = SagaTestFixture<StubSaga>()
        fixture {
            register {
                commandGateway<StubGateway>()
            }
            given { noPriorActivity() }
            whenever {
                // this will create a message with a timestamp from the real time. It should be converted to fixture-time
                uuidString() publishes GenericEventMessage.asEventMessage<TriggerSagaStartEvent>(TriggerSagaStartEvent(identifier.toString()))
            }
            expect {
                scheduledEvent(Duration.ofMinutes(10)).ofType<TimerTriggeredEvent>()
            }
        }
    }

    @Test
    fun testScheduledEventsInPastAsDomainEventMessage() {
        val identifier = UUID.randomUUID()
        val fixture = SagaTestFixture<StubSaga>()
        fixture {
            register { commandGateway<StubGateway>() }
            given {
                uuidString() published GenericEventMessage.asEventMessage<TriggerSagaStartEvent>(TriggerSagaStartEvent(identifier.toString()))
            }
            whenever { timeElapses(Duration.ofMinutes(1)) }
            expect { scheduledEvent(Duration.ofMinutes(9)).ofType<TimerTriggeredEvent>() }
        }
    }

    @Test
    fun testScheduledEventsInPastAsEventMessage() {
        val identifier = UUID.randomUUID()
        val fixture = SagaTestFixture<StubSaga>()
        fixture {
            register { commandGateway<StubGateway>() }
            given { publishedInOrder { +GenericEventMessage.asEventMessage<TriggerSagaStartEvent>(TriggerSagaStartEvent(identifier.toString())) } }
            whenever { timeElapses(Duration.ofMinutes(1)) }
            expect { scheduledEvent(Duration.ofMinutes(9)).ofType<TimerTriggeredEvent>() }
        }
    }

    @Test
    fun testFixtureApi_givenCurrentTime() {
        val identifier = UUID.randomUUID().toString()
        val fourDaysAgo = Instant.now().minus(4, ChronoUnit.DAYS)
        val fourDaysMinusTenMinutesAgo = fourDaysAgo.plus(10, ChronoUnit.MINUTES)

        val fixture = SagaTestFixture<StubSaga>()
        fixture {
            given {
                currentTime(fourDaysAgo)
            }
            whenever { publishing(TriggerSagaStartEvent(identifier)) }
            expect {
                scheduledEvent(fourDaysMinusTenMinutesAgo).of(TimerTriggeredEvent(identifier))
            }
        }
    }

    @Test
    fun testFixtureApi_WhenTimeElapses_UsingDefaults() {
        val identifier = UUID.randomUUID().toString()
        val identifier2 = UUID.randomUUID().toString()
        val fixture = SagaTestFixture<StubSaga>()

        fixture {
            register { commandGateway<StubGateway>() }
            given {
                identifier published TriggerSagaStartEvent(identifier)
                identifier2 published TriggerExistingSagaEvent(identifier2)
            }
            whenever { timeElapses(Duration.ofMinutes(35)) }
            expect {
                activeSagas = 1
                "identifier" associatedWith identifier
                "identifier" notAssociatedWith identifier2
                noScheduledEvents()
                // since we return null for the command, the other is never sent...
                dispatchedCommands { +"Say hi!" }
                publishedEventsMatching(noEvents())
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testFixtureApi_WhenTimeElapses_UsingCallbackBehavior() {
        val identifier = UUID.randomUUID().toString()
        val identifier2 = UUID.randomUUID().toString()
        val fixture = SagaTestFixture<StubSaga>()
        val commandHandler = mock<CallbackBehavior>()
        whenever(commandHandler.handle(eq("Say hi!"), isA())).thenReturn("Hi again!")

        fixture {
            callbackBehavior(commandHandler)
            register {
                commandGateway<StubGateway>()
            }
            given {
                identifier published TriggerSagaStartEvent(identifier)
            }
            whenever {
                timeElapses(Duration.ofMinutes(35))
            }
            expect {
                activeSagas = 1
                "identifier" associatedWith identifier
                "identifier" notAssociatedWith identifier2
                noScheduledEvents()
                dispatchedCommands {
                    +"Say hi!"
                    +"Hi again!"
                }
                publishedEventsMatching(noEvents())
            }
        }

        verify(commandHandler, times(2)).handle(isA(), eq(MetaData.emptyInstance()))
    }

    @Test
    fun testFixtureApi_WhenTimeAdvances() {
        val identifier = UUID.randomUUID().toString()
        val identifier2 = UUID.randomUUID().toString()
        val fixture = SagaTestFixture<StubSaga>()
        fixture {
            register {
                commandGateway<StubGateway>()
            }
            given {
                identifier published TriggerSagaStartEvent(identifier)
                identifier2 published TriggerExistingSagaEvent(identifier2)
            }
            whenever { timeAdvancesTo(Instant.now().plus(Duration.ofDays(1))) }
            expect {
                activeSagas = 1
                "identifier" associatedWith identifier
                "identifier" notAssociatedWith identifier2
                noScheduledEvents()
                dispatchedCommands { +"Say hi!" }
            }
        }
    }

    @Test
    fun testLastResourceEvaluatedFirst() {
        val identifier = UUID.randomUUID().toString()
        val identifier2 = UUID.randomUUID().toString()
        val fixture = SagaTestFixture<StubSaga>()
        val mock = mock<StubGateway>()
        fixture {
            register {
                commandGateway(mock)
            }
            given {
                identifier published TriggerSagaStartEvent(identifier)
                identifier2 published TriggerExistingSagaEvent(identifier2)
            }
            whenever { timeAdvancesTo(Instant.now().plus(Duration.ofDays(1))) }
            expect {
                activeSagas = 1
                "identifier" associatedWith identifier
                "identifier" notAssociatedWith identifier2
                noScheduledEvents()
                dispatchedCommands { +"Say hi!" }
            }
        }
        verify(mock).send(any())
    }
}
