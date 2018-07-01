package com.tylerthrailkill.axon_kotlin.axonframework.test.saga.dsl

import com.tylerthrailkill.axon_kotlin.axonframework.test.exceptions.AxonKotlinTestException
import org.axonframework.eventhandling.EventMessage
import org.axonframework.test.FixtureExecutionException
import org.axonframework.test.matchers.FieldFilter
import org.axonframework.test.saga.ContinuedGivenState
import org.axonframework.test.saga.FixtureConfiguration
import org.axonframework.test.saga.FixtureExecutionResult
import org.axonframework.test.saga.WhenState
import org.axonframework.test.utils.CallbackBehavior
import org.hamcrest.Matcher
import java.time.Duration
import java.time.Instant


operator fun FixtureConfiguration.invoke(init: SagaTestFixtureBuilder.() -> Unit): SagaTestFixtureBuilder {
    val fixture = SagaTestFixtureBuilder(this)
    fixture.init()
    return fixture
}

/**
 * ```
 *  FixtureExecutionResult validator = fixture
 *    .givenAggregate(aggregate1).published(
 *        GenericEventMessage.asEventMessage(new TriggerSagaStartEvent(aggregate1)), new TriggerExistingSagaEvent(aggregate1))
 *    .andThenAggregate(aggregate2).published(new TriggerSagaStartEvent(aggregate2))
 *    .whenAggregate(aggregate1).publishes(new TriggerSagaEndEvent(aggregate1))
 *
 *    validator.expectActiveSagas(1)
 *    validator.expectAssociationWith("identifier", aggregate2)
 *    validator.expectNoAssociationWith("identifier", aggregate1)
 *    validator.expectScheduledEventOfType(Duration.ofMinutes(10), TimerTriggeredEvent::class.java)
 *    validator.expectScheduledEventMatching(Duration.ofMinutes(10), messageWithPayload(CoreMatchers.any(TimerTriggeredEvent::class.java)))
 *    validator.expectScheduledEvent(Duration.ofMinutes(10), TimerTriggeredEvent(aggregate1))
 *    validator.expectScheduledEventOfType(fixture.currentTime().plusSeconds(600), TimerTriggeredEvent::class.java)
 *    validator.expectScheduledEventMatching(fixture.currentTime().plusSeconds(600), messageWithPayload(CoreMatchers.any(TimerTriggeredEvent::class.java)))
 *    validator.expectScheduledEvent(fixture.currentTime().plusSeconds(600), TimerTriggeredEvent(aggregate1))
 *    validator.expectDispatchedCommands()
 *    validator.expectNoDispatchedCommands()
 *    validator.expectPublishedEventsMatching(noEvents())
 * ```
 *
 * translates to
 *
 * ```
 * fixture {
 *   given {
 *     aggregate1 published GenericEventMessage.asEventMessage(TriggerSagaStartEvent(aggregate1)), TriggerExistingSagaEvent(aggregate1))
 *     aggregate2 published TriggerSagaStartEvent(aggregate2)
 *   }
 *   whenever {
 *     aggregate1 publishes TriggerSagaEndEvent(aggregate1)
 *   }
 *   expect {
 *     activeSagas(1)
 *     "identifier" associatedWith aggregate2
 *     "identifier" notAssociatedWith aggregate1
 *     scheduledEvent(Duration.ofMinutes(10)).ofType<TimerTriggeredEvent()
 *     scheduledEvent(Duration.ofMinutes(10).matching(messageWithPayload(any<TimerTriggeredEvent>()))
 *     scheduledEvent(Duration.ofMinutes(10).of(TimerTriggeredEvent(aggregate1))
 *     dispatchedCommands {}
 *     noDispatchedCommands()
 *     publishedEventsMatching = noEvents()
 *   }
 * ```
 */
class SagaTestFixtureBuilder(val sagaTestFixture: FixtureConfiguration) {
    private val registerBuilder: RegisterBuilder = RegisterBuilder()
    private val givenBuilder: GivenBuilder = GivenBuilder()
    lateinit var continuedGivenState: WhenState
    lateinit var fixtureExecutionResult: FixtureExecutionResult

    fun callbackBehavior(callbackBehavior: CallbackBehavior) {
        sagaTestFixture.setCallbackBehavior(callbackBehavior)
    }

    fun register(block: RegisterBuilder.() -> Unit) = registerBuilder.apply(block)

    fun given(block: GivenBuilder.() -> Unit): WhenState {
        continuedGivenState = givenBuilder.apply(block).continuedGivenState
        return continuedGivenState
    }

    fun whenever(block: WheneverBuilder.() -> Unit): FixtureExecutionResult {
        fixtureExecutionResult = WheneverBuilder(continuedGivenState).apply(block).fixtureExecutionResult
        return fixtureExecutionResult
    }

    fun expect(block: ExpectsBuilder.() -> Unit) {
        ExpectsBuilder(fixtureExecutionResult).apply(block)
    }

    inner class RegisterBuilder {
        private var resourcesBuilder: AnyUnaryBuilder = AnyUnaryBuilder()
        private var fieldFilterBuilder: RegisterFieldFiltersBuilder = RegisterFieldFiltersBuilder()
        var callbackBehavior: CallbackBehavior? = null
            set(value) {
                sagaTestFixture.setCallbackBehavior(value)
            }

        fun transienceCheckDisabled() {
            sagaTestFixture.withTransienceCheckDisabled()
        }

        fun resources(builder: AnyUnaryBuilder.() -> Unit) {
            resourcesBuilder.apply(builder).list.forEach {
                sagaTestFixture.registerResource(it)
            }
        }

        inline fun <reified C> commandGateway(commandGateway: C? = null) {
            sagaTestFixture.registerCommandGateway(C::class.java, commandGateway)
        }

        inner class RegisterFieldFiltersBuilder {
            val list = mutableListOf<FieldFilter>()
            operator fun FieldFilter.unaryPlus() {
                list.add(this)
            }
        }

        fun fieldFilters(builder: RegisterFieldFiltersBuilder.() -> Unit) {
            fieldFilterBuilder.apply(builder).list.forEach {
                sagaTestFixture.registerFieldFilter(it)
            }
        }

        fun fieldFilter(fieldFilter: FieldFilter) {
            sagaTestFixture.registerFieldFilter(fieldFilter)
        }

        inline fun <reified C> ignoredField(fieldName: String) {
            sagaTestFixture.registerIgnoredField(C::class.java, fieldName)
        }

    }

    inner class GivenBuilder {
        private var state: ContinuedGivenState? = null
        val continuedGivenState: ContinuedGivenState
            get() {
                return state ?: throw AxonKotlinTestException("given {} block was never instantiated")
            }

        infix fun String.published(block: AnyUnaryBuilder.() -> Unit) {
            val list = AnyUnaryBuilder().apply(block).list
            val currentState = state
            state = currentState?.let { currentState.andThenAggregate(this).published(list) }
                    ?: sagaTestFixture.givenAggregate(this).published(*list.toTypedArray())
        }

        infix fun String.published(event: Any) {
            val currentState = state
            state = currentState?.let { currentState.andThenAggregate(this).published(event) }
                    ?: sagaTestFixture.givenAggregate(this).published(event)
        }

        fun noPriorActivity() {
            state = sagaTestFixture.givenNoPriorActivity() as ContinuedGivenState
        }

        fun publishedInOrder(block: AnyUnaryBuilder.() -> Unit) {
            AnyUnaryBuilder().apply(block).list.forEach {item ->
                val currentState = state
                state = currentState?.let { currentState.andThenAPublished(item) }
                        ?: sagaTestFixture.givenAPublished(item)
            }
        }

        fun thenTimeElapses(elapsedTime: Duration) {
            state?.andThenTimeElapses(elapsedTime)
        }

        fun thenTimeAdvancesTo(newDateTime: Instant) {
            state?.andThenTimeAdvancesTo(newDateTime)
        }

        fun currentTime(currentTime: Instant?) {
            state = sagaTestFixture.givenCurrentTime(currentTime)
        }
    }

    inner class WheneverBuilder(val whenState: WhenState) {
        private var _state: FixtureExecutionResult? = null
        val fixtureExecutionResult: FixtureExecutionResult
            get() {
                return _state ?: throw AxonKotlinTestException("")
            }

        infix fun String.publishes(event: Any) {
            _state = whenState.whenAggregate(this).publishes(event)
        }

        fun publishing(event: Any) {
            _state = whenState.whenPublishingA(event)
        }

        fun timeElapses(elapsedTime: Duration) {
           _state = whenState.whenTimeElapses(elapsedTime)
        }

        fun timeAdvancesTo(newDateTime: Instant) {
            _state = whenState.whenTimeAdvancesTo(newDateTime)
        }
    }

    inner class ExpectsBuilder(val fixtureExecutionResult: FixtureExecutionResult) {
        var activeSagas: Int = 0 // default so that non nullable. Since only setter calls this method, this is safe
            set(value) {
                fixtureExecutionResult.expectActiveSagas(value)
            }

        infix fun String.associatedWith(thing: Any) {
            fixtureExecutionResult.expectAssociationWith(this, thing)
        }

        infix fun String.notAssociatedWith(thing: Any) {
            fixtureExecutionResult.expectNoAssociationWith(this, thing)
        }

        fun scheduledEvent(duration: Duration): ScheduledEvent = ScheduledEvent(duration = duration)
        fun scheduledEvent(instant: Instant): ScheduledEvent = ScheduledEvent(instant = instant)

        fun noScheduledEvents() {
            fixtureExecutionResult.expectNoScheduledEvents()
        }

        fun dispatchedCommands(block: AnyUnaryBuilder.() -> Unit) {
            val list = AnyUnaryBuilder().apply(block).list
            fixtureExecutionResult.expectDispatchedCommands(*list.toTypedArray())
        }

        fun noDispatchedCommands() {
            fixtureExecutionResult.expectNoDispatchedCommands()
        }

        fun publishedEventsMatching(matcher: Matcher<out MutableList<in EventMessage<*>>>) {
            fixtureExecutionResult.expectPublishedEventsMatching(matcher)
        }

        fun publishedEvents(block: AnyUnaryBuilder.() -> Unit) {
            val list = AnyUnaryBuilder().apply(block).list
            fixtureExecutionResult.expectPublishedEvents(*list.toTypedArray())
        }

        inner class ScheduledEvent(val duration: Duration? = null, val instant: Instant? = null) {
            init {
                if (duration == null && instant == null) {
                    throw AxonKotlinTestException("one type of duration must be provided")
                }
                if (duration != null && instant != null) {
                    throw AxonKotlinTestException("only one type of duration can be provided")
                }
            }

            inline fun <reified T> ofType() {
                duration?.let { fixtureExecutionResult.expectScheduledEventOfType(duration, T::class.java) }
                        ?: fixtureExecutionResult.expectScheduledEventOfType(instant, T::class.java)
            }

            fun matching(matcher: Matcher<in EventMessage<*>>) {
                duration?.let { fixtureExecutionResult.expectScheduledEventMatching(duration, matcher) }
                        ?: fixtureExecutionResult.expectScheduledEventMatching(instant, matcher)
            }

            fun of(event: Any) {
                duration?.let { fixtureExecutionResult.expectScheduledEvent(duration, event) }
                        ?: fixtureExecutionResult.expectScheduledEvent(instant, event)
            }

        }
    }

    class AnyUnaryBuilder {
        val list = mutableListOf<Any>()
        operator fun Any.unaryPlus() {
            list.add(this)
        }
    }
}

