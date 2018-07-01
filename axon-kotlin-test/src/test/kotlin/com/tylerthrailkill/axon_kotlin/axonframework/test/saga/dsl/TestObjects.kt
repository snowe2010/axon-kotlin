package com.tylerthrailkill.axon_kotlin.axonframework.test.saga.dsl

import org.axonframework.eventhandling.EventBus
import org.axonframework.eventhandling.EventMessage
import org.axonframework.eventhandling.GenericEventMessage
import org.axonframework.eventhandling.Timestamp
import org.axonframework.eventhandling.saga.EndSaga
import org.axonframework.eventhandling.saga.SagaEventHandler
import org.axonframework.eventhandling.saga.SagaLifecycle
import org.axonframework.eventhandling.saga.StartSaga
import org.axonframework.eventhandling.scheduling.EventScheduler
import org.axonframework.eventhandling.scheduling.ScheduleToken
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import javax.inject.Inject

class ForceTriggerSagaStartEvent(val identifier: String)
class NonTransientResource
class ResetTriggerEvent(val identifier: String)
class SagaWasTriggeredEvent(val triggeredSaga: StubSaga)
class TimerTriggeredEvent(val identifier: String)
class TriggerExceptionWhileHandlingEvent(val identifier: String)
class TriggerExistingSagaEvent(val identifier: String)
class TriggerSagaEndEvent(val identifier: String)
class TriggerSagaStartEvent(val identifier: String)
class MyOtherEvent

interface StubGateway {
    fun send(s: String): String?
}


/**
 * @author Allard Buijze
 */
class StubSaga {
    @Autowired
    @Transient private val stubGateway: StubGateway? = null
    @Inject
    @Transient val scheduler: EventScheduler? = null
    @Inject
    private val nonTransientResource: NonTransientResource? = null

    private val handledEvents = ArrayList<Any>()
    private var timer: ScheduleToken? = null

    @StartSaga
    @SagaEventHandler(associationProperty = "identifier")
    fun handleSagaStart(event: TriggerSagaStartEvent, message: EventMessage<TriggerSagaStartEvent>) {
        handledEvents.add(event)
        timer = scheduler!!.schedule(message.getTimestamp().plus(TRIGGER_DURATION_MINUTES.toLong(), ChronoUnit.MINUTES),
                GenericEventMessage<Any>(TimerTriggeredEvent(event.identifier)))
    }

    @StartSaga(forceNew = true)
    @SagaEventHandler(associationProperty = "identifier")
    fun handleForcedSagaStart(event: ForceTriggerSagaStartEvent, @Timestamp timestamp: Instant) {
        handledEvents.add(event)
        timer = scheduler!!.schedule(timestamp.plus(TRIGGER_DURATION_MINUTES.toLong(), ChronoUnit.MINUTES),
                GenericEventMessage<Any>(TimerTriggeredEvent(event.identifier)))
    }

    @SagaEventHandler(associationProperty = "identifier")
    fun handleEvent(event: TriggerExistingSagaEvent, eventBus: EventBus) {
        handledEvents.add(event)
        eventBus.publish(GenericEventMessage<Any>(SagaWasTriggeredEvent(this)))
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "identifier")
    fun handleEndEvent(event: TriggerSagaEndEvent) {
        handledEvents.add(event)
    }

    @SagaEventHandler(associationProperty = "identifier")
    fun handleFalseEvent(event: TriggerExceptionWhileHandlingEvent) {
        handledEvents.add(event)
        throw RuntimeException("This is a mock exception")
    }

    @SagaEventHandler(associationProperty = "identifier")
    fun handleTriggerEvent(event: TimerTriggeredEvent) {
        handledEvents.add(event)
        val result = stubGateway!!.send("Say hi!")
        if (result != null) {
            stubGateway.send(result)
        }
    }

    @SagaEventHandler(associationProperty = "identifier")
    fun handleResetTriggerEvent(event: ResetTriggerEvent) {
        handledEvents.add(event)
        scheduler!!.cancelSchedule(timer)
        timer = scheduler.schedule(Duration.ofMinutes(TRIGGER_DURATION_MINUTES.toLong()),
                GenericEventMessage<Any>(TimerTriggeredEvent(event.identifier)))
    }

    fun associateWith(key: String, value: String) {
        SagaLifecycle.associateWith(key, value)
    }

    fun removeAssociationWith(key: String, value: String) {
        SagaLifecycle.removeAssociationWith(key, value)
    }

    fun end() {
        SagaLifecycle.end()
    }

    companion object {

        private val TRIGGER_DURATION_MINUTES = 10
    }
}
