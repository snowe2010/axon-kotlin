package com.tylerthrailkill.axon_kotlin.axonframework.test.saga.extensions

import com.nhaarman.mockito_kotlin.any
import com.sun.org.apache.xerces.internal.impl.xpath.regex.Match
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.eventhandling.EventBus
import org.axonframework.eventhandling.EventMessage
import org.axonframework.eventhandling.saga.repository.inmemory.InMemorySagaStore
import org.axonframework.test.eventscheduler.StubEventScheduler
import org.axonframework.test.matchers.FieldFilter
import org.axonframework.test.matchers.Matchers
import org.axonframework.test.matchers.Matchers.equalTo
import org.axonframework.test.matchers.Matchers.messageWithPayload
import org.axonframework.test.saga.CommandValidator
import org.axonframework.test.saga.EventSchedulerValidator
import org.axonframework.test.saga.EventValidator
import org.axonframework.test.saga.FixtureExecutionResult
import org.axonframework.test.utils.RecordingCommandBus
import org.hamcrest.Matcher
import java.time.Duration
import java.time.Instant


/**
 * Default implementation of FixtureExecutionResult.
 *
 * @author Allard Buijze
 * @since 1.1
 */
class FixtureExecutionResultImpl<T>
/**
 * Initializes an instance and make it monitor the given infrastructure classes.
 *
 * @param sagaStore      The SagaStore to monitor
 * @param eventScheduler The scheduler to monitor
 * @param eventBus       The event bus to monitor
 * @param commandBus     The command bus to monitor
 * @param sagaType       The type of Saga under test
 * @param fieldFilter    The FieldFilter describing the fields to include in equality checks
 */
internal constructor(sagaStore: InMemorySagaStore, eventScheduler: StubEventScheduler,
                     eventBus: EventBus, commandBus: RecordingCommandBus,
                     sagaType: Class<T>, private val fieldFilter: FieldFilter) : FixtureExecutionResult {

    private val repositoryContentValidator: RepositoryContentValidator<T>
    private val eventValidator: EventValidator
    private val eventSchedulerValidator: EventSchedulerValidator
    private val commandValidator: CommandValidator

    init {
        commandValidator = CommandValidator(commandBus, fieldFilter)
        repositoryContentValidator = RepositoryContentValidator(sagaType, sagaStore)
        eventValidator = EventValidator(eventBus, fieldFilter)
        eventSchedulerValidator = EventSchedulerValidator(eventScheduler)
    }

    /**
     * Tells this class to start monitoring activity in infrastructure classes.
     */
    fun startRecording() {
        eventValidator.startRecording()
        commandValidator.startRecording()
    }

    override fun expectActiveSagas(expected: Int): FixtureExecutionResult {
        repositoryContentValidator.assertActiveSagas(expected)
        return this
    }

    override fun expectAssociationWith(associationKey: String, associationValue: Any): FixtureExecutionResult {
        repositoryContentValidator.assertAssociationPresent(associationKey, associationValue.toString())
        return this
    }

    override fun expectNoAssociationWith(associationKey: String, associationValue: Any): FixtureExecutionResult {
        repositoryContentValidator.assertNoAssociationPresent(associationKey, associationValue.toString())
        return this
    }


    override fun expectScheduledEventMatching(duration: Duration, matcher: Matcher<in EventMessage<*>>): FixtureExecutionResult {
        eventSchedulerValidator.assertScheduledEventMatching(duration, matcher)
        return this
    }

    override fun expectScheduledEvent(duration: Duration, applicationEvent: Any): FixtureExecutionResult {
        return expectScheduledEventMatching(duration, messageWithPayload(equalTo<Any>(applicationEvent, fieldFilter)))
    }

    override fun expectScheduledEventOfType(duration: Duration, eventType: Class<*>): FixtureExecutionResult {
        return expectScheduledEventMatching(duration, messageWithPayload(org.hamcrest.CoreMatchers.any(eventType)))
    }

    override fun expectScheduledEventMatching(scheduledTime: Instant, matcher: Matcher<in EventMessage<*>>): FixtureExecutionResult {
        eventSchedulerValidator.assertScheduledEventMatching(scheduledTime, matcher)
        return this
    }

    override fun expectScheduledEvent(scheduledTime: Instant, applicationEvent: Any): FixtureExecutionResult {
        return expectScheduledEventMatching(scheduledTime, messageWithPayload(equalTo<Any>(applicationEvent, fieldFilter)))
    }

    override fun expectScheduledEventOfType(scheduledTime: Instant, eventType: Class<*>): FixtureExecutionResult {
        return expectScheduledEventMatching(scheduledTime, messageWithPayload(any()))
    }

    override fun expectDispatchedCommands(vararg expected: Any): FixtureExecutionResult {
        commandValidator.assertDispatchedEqualTo(*expected)
        return this
    }

    override fun expectDispatchedCommandsMatching(matcher: Matcher<out MutableList<in CommandMessage<*>>>?): FixtureExecutionResult {
        commandValidator.assertDispatchedMatching(matcher)
        return this
    }

    override fun expectNoDispatchedCommands(): FixtureExecutionResult {
        commandValidator.assertDispatchedMatching(Matchers.noCommands())
        return this
    }

    override fun expectNoScheduledEvents(): FixtureExecutionResult {
        eventSchedulerValidator.assertNoScheduledEvents()
        return this
    }

    override fun expectPublishedEventsMatching(matcher: Matcher<out MutableList<in EventMessage<*>>>?): FixtureExecutionResult {
        eventValidator.assertPublishedEventsMatching(matcher)
        return this
    }

    override fun expectPublishedEvents(vararg expected: Any): FixtureExecutionResult {
        eventValidator.assertPublishedEvents(*expected)
        return this
    }
}
