package com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.dsl

import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.whenever
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.eventhandling.EventMessage
import org.axonframework.eventsourcing.AggregateFactory
import org.axonframework.eventsourcing.EventSourcingRepository
import org.axonframework.messaging.MessageDispatchInterceptor
import org.axonframework.messaging.MessageHandler
import org.axonframework.messaging.MetaData
import org.axonframework.test.aggregate.FixtureConfiguration
import org.axonframework.test.aggregate.ResultValidator
import org.axonframework.test.aggregate.TestExecutor
import org.hamcrest.Matcher


operator fun <T> FixtureConfiguration<T>.invoke(init: AggregateTestFixtureBuilder<T>.() -> Unit): FixtureConfiguration<T> {
    val fixture = AggregateTestFixtureBuilder(this)
    fixture.init()
    fixture.build()
    return fixture.aggregateTestFixture
}

/**
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
 */
class AggregateTestFixtureBuilder<T>(val aggregateTestFixture: FixtureConfiguration<T>) {
    private var wheneverCommand: Any? = null
    private var wheneverMetaData: Map<String, Any> = MetaData.emptyInstance()
    private val expectsBuilder: ExpectsBuilder<T> = ExpectsBuilder(aggregateTestFixture)
    private val givenBuilder: GivenBuilder<T> = GivenBuilder(aggregateTestFixture)
    private val registerBuilder: RegisterBuilder<T> = RegisterBuilder(aggregateTestFixture)

    fun register(block: RegisterBuilder<T>.() -> Unit) = registerBuilder.apply(block)

    fun given(block: GivenBuilder<T>.() -> Unit) = givenBuilder.apply(block)

    fun whenever(block: () -> Any) {
        val out = block()
        when (out) {
            is Pair<*, *> -> {
                wheneverCommand = out.first
                wheneverMetaData = out.second as Map<String, Any>
            }
            else -> {
                wheneverCommand = out
            }
        }
    }

    fun expect(block: ExpectsBuilder<T>.() -> Unit) = expectsBuilder.apply(block)

    fun build(): ResultValidator {
        buildFixtureConfiguration()
        val testExecutor = buildTestExecutor()
        return buildResultValidator(testExecutor)
    }

    private fun buildFixtureConfiguration(): FixtureConfiguration<T> {
        registerBuilder.repository?.let { aggregateTestFixture.registerRepository(it) }
        registerBuilder.aggregateFactory?.let { aggregateTestFixture.registerAggregateFactory(it) }
        return aggregateTestFixture
    }

    private fun buildTestExecutor(): TestExecutor {
        givenBuilder.initialize()
        return givenBuilder.testExecutor
    }

    private fun buildResultValidator(testExecutor: TestExecutor): ResultValidator {
        val resultValidator = testExecutor.whenever(wheneverCommand!!, wheneverMetaData)
        if (expectsBuilder.eventsSet) expectsBuilder.eventsBuilder.list.let { resultValidator.expectEvents(*it.toTypedArray()) }
        expectsBuilder.eventsMatching?.let { resultValidator.expectEventsMatching(it) }
        expectsBuilder.returnValue?.let { resultValidator.expectReturnValue(it) }
        expectsBuilder.returnValueMatching?.let { resultValidator.expectReturnValueMatching(it) }
        expectsBuilder.exception?.let { resultValidator.expectException(it) }
        expectsBuilder.successfulHandlerExecution?.let { resultValidator.expectSuccessfulHandlerExecution() }
        return resultValidator
    }

    data class RegisterBuilder<T>(
            var aggregateTestFixture: FixtureConfiguration<T>,
            var repository: EventSourcingRepository<T>? = null
    ) {
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

        class RegisterCommandDispatchInterceptorBuilder {
            val list = mutableListOf<MessageDispatchInterceptor<CommandMessage<*>>>()
            operator fun MessageDispatchInterceptor<CommandMessage<*>>.unaryPlus() {
                list.add(this)
            }
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

        fun injectableResources(builder: AnyUnaryBuilder.() -> Unit) {
            injectableResourcesBuilder.apply(builder).list.forEach {
                aggregateTestFixture.registerInjectableResource(it)
            }
        }

    }

    class GivenBuilder<T>(val aggregateTestFixture: FixtureConfiguration<T>) {
        val eventsBuilder: AggregateTestFixtureBuilder.AnyUnaryBuilder = AggregateTestFixtureBuilder.AnyUnaryBuilder()
        val commandsBuilder: AggregateTestFixtureBuilder.AnyUnaryBuilder = AggregateTestFixtureBuilder.AnyUnaryBuilder()
        lateinit var testExecutor: TestExecutor

        fun initialize() {
            if (!this::testExecutor.isInitialized)
                testExecutor = aggregateTestFixture.givenNoPriorActivity()
        }

        fun events(builder: AggregateTestFixtureBuilder.AnyUnaryBuilder.() -> Unit) {
            initialize()
            val list = eventsBuilder.apply(builder).list
            testExecutor.andGiven(list)
        }

        fun commands(builder: AggregateTestFixtureBuilder.AnyUnaryBuilder.() -> Unit) {
            initialize()
            val list = commandsBuilder.apply(builder).list
            testExecutor.andGivenCommands(list)
        }

        fun build() {
            if (!this::testExecutor.isInitialized)
                testExecutor = aggregateTestFixture.givenNoPriorActivity()
        }
    }

    data class ExpectsBuilder<T>(
            val aggregateTestFixture: FixtureConfiguration<T>,
            var returnValue: Any? = null,
            var returnValueMatching: Matcher<*>? = null,
            var noEvents: Boolean? = null,
            var eventsMatching: Matcher<out MutableList<in EventMessage<*>>>? = null,
            var exception: Matcher<*>? = null
    ) {
        var successfulHandlerExecution: Boolean? = null
            private set
        val eventsBuilder: AnyUnaryBuilder = AnyUnaryBuilder()
        val commandsBuilder: AnyUnaryBuilder = AnyUnaryBuilder()

        var commandsSet: Boolean = false
        var eventsSet: Boolean = false

        fun events(builder: AnyUnaryBuilder.() -> Unit) {
            eventsSet = true
            eventsBuilder.apply(builder).list
        }

        fun commands(builder: AnyUnaryBuilder.() -> Unit) {
            commandsSet = true
            commandsBuilder.apply(builder).list
        }

        fun withSuccessfulHandlerExecution() {
            successfulHandlerExecution = true
        }
    }

    class AnyUnaryBuilder {
        val list = mutableListOf<Any>()
        operator fun Any.unaryPlus() {
            list.add(this)
        }
    }
}

