package com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.dsl

import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.whenever
import com.tylerthrailkill.axon_kotlin.axonframework.test.exceptions.AxonKotlinTestException
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.eventhandling.EventMessage
import org.axonframework.eventsourcing.AggregateFactory
import org.axonframework.eventsourcing.EventSourcingRepository
import org.axonframework.messaging.MessageDispatchInterceptor
import org.axonframework.messaging.MessageHandler
import org.axonframework.messaging.MessageHandlerInterceptor
import org.axonframework.messaging.MetaData
import org.axonframework.test.aggregate.FixtureConfiguration
import org.axonframework.test.aggregate.ResultValidator
import org.axonframework.test.aggregate.TestExecutor
import org.axonframework.test.matchers.FieldFilter
import org.hamcrest.Matcher


/**
 * Method for starting the axon-kotlin-test DSL
 *
 * @param init lambda to begin the dsl. Valid methods to call in the context of the lambda can be found in the
 * AggregateTestFixtureBuilder class
 * @return [AggregateTestFixtureBuilder] that allows retrieving any part of the [AggregateTestFixture] chain
 */
operator fun <T> FixtureConfiguration<T>.invoke(init: AggregateTestFixtureBuilder<T>.() -> Unit): AggregateTestFixtureBuilder<T> {
    val fixture = AggregateTestFixtureBuilder(this)
    fixture.init()
    return fixture
}

/**
 * Builder for the DSL. Declares all methods that can be called in the context
 * of the Aggregate FixtureConfiguration DSL
 *
 * ```
 * fixture {
 *   given {
 *     events(CreatedEvent)
 *     commands(StubCommand)
 *   }
 *   whenever(StubCommand)
 *   expect {
 *     events = listOf(StubEvent)
 *   }
 * ```
 *
 * @param aggregateTestFixture takes in the [FixtureConfiguration] to build up
 */
class AggregateTestFixtureBuilder<T>(val aggregateTestFixture: FixtureConfiguration<T>) {
    var reportIllegalStateChange: Boolean = true
        set(value) {
            aggregateTestFixture.setReportIllegalStateChange(value)
        }
    private val givenBuilder: GivenBuilder<T> = GivenBuilder(aggregateTestFixture)
    private val registerBuilder: RegisterBuilder<T> = RegisterBuilder(aggregateTestFixture)
    lateinit var testExecutor: TestExecutor
    private var _resultValidator: ResultValidator? = null
    val resultValidator: ResultValidator
        get() = _resultValidator ?: throw AxonKotlinTestException("Result Validator was not created in `fixture` block")

    fun register(block: RegisterBuilder<T>.() -> Unit) = registerBuilder.apply(block)

    fun given(block: GivenBuilder<T>.() -> Unit): TestExecutor {
        givenBuilder.apply(block)
        givenBuilder.initialize()
        testExecutor = givenBuilder.testExecutor
        return testExecutor
    }

    fun whenever(block: () -> Any): ResultValidator {
        // Initialize test executor if it hasn't been already. This is only necessary if the user doesn't provide a `given` block
        givenBuilder.initialize()
        testExecutor = givenBuilder.testExecutor

        val wheneverCommand: Any
        var wheneverMetaData: Map<String, Any> = MetaData.emptyInstance()

        val out = block()
        when (out) {
            is Pair<*, *> -> {
                wheneverCommand = out.first ?: throw AxonKotlinTestException("First argument of whenever call must be non null")
                wheneverMetaData = out.second as Map<String, Any>
            }
            else -> {
                wheneverCommand = out
            }
        }
        _resultValidator = testExecutor.whenever(wheneverCommand, wheneverMetaData)
        return _resultValidator ?: throw AxonKotlinTestException("Result Validator was unable to be created")
    }

    fun expect(block: ExpectsBuilder.() -> Unit): ExpectsBuilder {
        return _resultValidator?.let { ExpectsBuilder(it).apply(block) }
                ?: throw AxonKotlinTestException("Expect block cannot be used unless a whenever block is present before")
    }

    data class RegisterBuilder<T>(
            var aggregateTestFixture: FixtureConfiguration<T>
    ) {
        var repository: EventSourcingRepository<T>? = null
            set(value) {
                aggregateTestFixture = aggregateTestFixture.registerRepository(value)
            }
        /**
         * Aggregate factory has to be registered immediately else a repository might be set up before this is called
         */
        var aggregateFactory: AggregateFactory<T>? = null
            set(value) {
                aggregateTestFixture = aggregateTestFixture.registerAggregateFactory(value)
            }
        var annotatedCommandHandler: Any? = null
            set(value) {
                aggregateTestFixture = aggregateTestFixture.registerAnnotatedCommandHandler(value)
            }
        private var injectableResourcesBuilder: AnyUnaryBuilder = AnyUnaryBuilder()
        private var commandDispatchInterceptorBuilder: RegisterCommandDispatchInterceptorBuilder = RegisterCommandDispatchInterceptorBuilder()
        private var commandHandlerInterceptorBuilder: RegisterCommandHandlerInterceptorBuilder = RegisterCommandHandlerInterceptorBuilder()
        private var fieldFilterBuilder: RegisterFieldFiltersBuilder = RegisterFieldFiltersBuilder()

        class RegisterCommandDispatchInterceptorBuilder {
            val list = mutableListOf<MessageDispatchInterceptor<CommandMessage<*>>>()
            operator fun MessageDispatchInterceptor<CommandMessage<*>>.unaryPlus() {
                list.add(this)
            }
        }

        class RegisterCommandHandlerInterceptorBuilder {
            val list = mutableListOf<MessageHandlerInterceptor<CommandMessage<*>>>()
            operator fun MessageHandlerInterceptor<CommandMessage<*>>.unaryPlus() {
                list.add(this)
            }
        }

        class RegisterFieldFiltersBuilder {
            val list = mutableListOf<FieldFilter>()
            operator fun FieldFilter.unaryPlus() {
                list.add(this)
            }
        }

        fun fieldFilters(builder: RegisterFieldFiltersBuilder.() -> Unit) {
            fieldFilterBuilder.apply(builder).list.forEach {
                aggregateTestFixture.registerFieldFilter(it)
            }
        }

        fun fieldFilter(fieldFilter: FieldFilter) {
            aggregateTestFixture.registerFieldFilter(fieldFilter)
        }

        inline fun <reified C> commandHandler(commandHandler: MessageHandler<CommandMessage<*>>) {
            aggregateTestFixture.registerCommandHandler(C::class.java, commandHandler)
        }

        fun commandHandler(commandName: String, commandHandler: MessageHandler<CommandMessage<*>>) {
            aggregateTestFixture.registerCommandHandler(commandName, commandHandler)
        }

        fun commandDispatchInterceptors(builder: RegisterCommandDispatchInterceptorBuilder.() -> Unit) {
            commandDispatchInterceptorBuilder.apply(builder).list.forEach {
                aggregateTestFixture.registerCommandDispatchInterceptor(it)
            }
        }

        fun commandHandlerInterceptors(builder: RegisterCommandHandlerInterceptorBuilder.() -> Unit) {
            commandHandlerInterceptorBuilder.apply(builder).list.forEach {
                aggregateTestFixture.registerCommandHandlerInterceptor(it)
            }
        }

        fun injectableResources(builder: AnyUnaryBuilder.() -> Unit) {
            injectableResourcesBuilder.apply(builder).list.forEach {
                aggregateTestFixture.registerInjectableResource(it)
            }
        }

    }

    class GivenBuilder<T>(private val aggregateTestFixture: FixtureConfiguration<T>) {
        lateinit var testExecutor: TestExecutor

        fun initialize() {
            if (!this::testExecutor.isInitialized)
                testExecutor = aggregateTestFixture.givenNoPriorActivity()
        }

        fun events(builder: AggregateTestFixtureBuilder.AnyUnaryBuilder.() -> Unit) {
            initialize()
            val list = AggregateTestFixtureBuilder.AnyUnaryBuilder().apply(builder).list
            testExecutor.andGiven(list)
        }

        fun commands(builder: AggregateTestFixtureBuilder.AnyUnaryBuilder.() -> Unit) {
            initialize()
            val list = AggregateTestFixtureBuilder.AnyUnaryBuilder().apply(builder).list
            testExecutor.andGivenCommands(list)
        }

        fun build() {
            if (!this::testExecutor.isInitialized)
                testExecutor = aggregateTestFixture.givenNoPriorActivity()
        }
    }

    class ExpectsBuilder(val resultValidator: ResultValidator) {
        private val eventsBuilder: AnyUnaryBuilder = AnyUnaryBuilder()

        var returnValue: Any? = null
            set(value) {
                resultValidator.expectReturnValue(value)
            }
        var returnValueMatching: Matcher<*>? = null
            set(value) {
                resultValidator.expectReturnValueMatching(value)
            }
        var noEvents: Boolean? = null
            set(_) {
                resultValidator.expectNoEvents()
            }
        var eventsMatching: Matcher<out MutableList<in EventMessage<*>>>? = null
            set(value) {
                resultValidator.expectEventsMatching(value)
            }

        fun events(builder: AnyUnaryBuilder.() -> Unit): ResultValidator {
            val list: MutableList<Any> = eventsBuilder.apply(builder).list
            return resultValidator.expectEvents(*list.toTypedArray())
        }

        fun successfulHandlerExecution(): ResultValidator = resultValidator.expectSuccessfulHandlerExecution()

        inline fun <reified T : Throwable> exception(): ResultValidator = resultValidator.expectException(T::class.java)
        fun exception(matcher: Matcher<*>): ResultValidator = resultValidator.expectException(matcher)
    }

    class AnyUnaryBuilder {
        val list = mutableListOf<Any>()
        operator fun Any.unaryPlus() {
            list.add(this)
        }
    }
}

