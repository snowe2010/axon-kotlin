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
import org.hamcrest.Matcher


operator fun <T> FixtureConfiguration<T>.invoke(init: AggregateTestFixtureBuilder<T>.() -> Unit): FixtureConfiguration<T> {
    val fixture = AggregateTestFixtureBuilder(this)
    fixture.init()
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
    private val givenBuilder: GivenBuilder<T> = GivenBuilder(aggregateTestFixture)
    private val registerBuilder: RegisterBuilder<T> = RegisterBuilder(aggregateTestFixture)
    private lateinit var testExecutor: TestExecutor
    private var resultValidator: ResultValidator? = null

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
        resultValidator = testExecutor.whenever(wheneverCommand, wheneverMetaData)
        return resultValidator ?: throw AxonKotlinTestException("Result Validator was unable to be created")
    }

    fun expect(block: ExpectsBuilder.() -> Unit): ExpectsBuilder {
        return resultValidator?.let { ExpectsBuilder(it).apply(block) }
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

    class GivenBuilder<T>(val aggregateTestFixture: FixtureConfiguration<T>) {
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

    data class ExpectsBuilder(
            val resultValidator: ResultValidator
    ) {
        var returnValue: Any? = null
            set(value) {
                resultValidator.expectReturnValue(value)
            }
        var returnValueMatching: Matcher<*>? = null
            set(value) {
                resultValidator.expectReturnValueMatching(value)
            }
        var noEvents: Boolean? = null
        var eventsMatching: Matcher<out MutableList<in EventMessage<*>>>? = null
            set(value) {
                resultValidator.expectEventsMatching(value)
            }
        val eventsBuilder: AnyUnaryBuilder = AnyUnaryBuilder()
        val commandsBuilder: AnyUnaryBuilder = AnyUnaryBuilder()

        var commandsSet: Boolean = false
        var eventsSet: Boolean = false

        fun events(builder: AnyUnaryBuilder.() -> Unit) {
            eventsSet = true
            val list: MutableList<Any> = eventsBuilder.apply(builder).list
            resultValidator.expectEvents(*list.toTypedArray())
        }

        fun commands(builder: AnyUnaryBuilder.() -> Unit) {
            commandsSet = true
            commandsBuilder.apply(builder).list
        }

        fun withSuccessfulHandlerExecution() {
            resultValidator.expectSuccessfulHandlerExecution()
        }

        inline fun <reified T : Throwable> exception() {
            resultValidator.expectException(T::class.java)
        }

        fun exception(matcher: Matcher<*>) {
            resultValidator.expectException(matcher)
        }
    }

    class AnyUnaryBuilder {
        val list = mutableListOf<Any>()
        operator fun Any.unaryPlus() {
            list.add(this)
        }
    }
}

