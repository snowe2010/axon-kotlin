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
    val fixture = AggregateTestFixtureBuilder<T>(this)
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
    private val expectsBuilder: ExpectsBuilder = ExpectsBuilder()
    private val givenBuilder: GivenBuilder = GivenBuilder()
    private val registerBuilder: RegisterBuilder<T> = RegisterBuilder()

    fun register(block: RegisterBuilder<T>.() -> Unit) = registerBuilder.apply(block)

    fun given(block: GivenBuilder.() -> Unit) = givenBuilder.apply(block)

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

    fun expect(block: ExpectsBuilder.() -> Unit) = expectsBuilder.apply(block)

    fun build(): ResultValidator {
        requireNotNull(wheneverCommand)
        requireNotNull(expectsBuilder)

        buildFixtureConfiguration()
        val testExecutor = buildTestExecutor()
        return buildResultValidator(testExecutor)
    }

    private fun buildFixtureConfiguration(): FixtureConfiguration<T> {
        registerBuilder.repository?.let { aggregateTestFixture.registerRepository(it) }
        registerBuilder.aggregateFactory?.let { aggregateTestFixture.registerAggregateFactory(it) }
        registerBuilder.annotatedCommandHandler?.let { aggregateTestFixture.registerAnnotatedCommandHandler(it) }
        registerBuilder.commandDispatchInterceptorBuilder.list.forEach {
            aggregateTestFixture.registerCommandDispatchInterceptor(it)
        }
        registerBuilder.commandHandlers.forEach { aggregateTestFixture.registerCommandHandler(it.key, it.value) }
        registerBuilder.injectableResourcesBuilder.list.forEach { aggregateTestFixture.registerInjectableResource(it) }
        return aggregateTestFixture
    }

    private fun buildTestExecutor(): TestExecutor {
        val eventsBuilderList = givenBuilder.eventsBuilder.list
        val commandsBuilderList = givenBuilder.commandsBuilder.list
        val testExecutor = aggregateTestFixture.givenNoPriorActivity()
        return if (eventsBuilderList.isNotEmpty() && commandsBuilderList.isEmpty()) {
            testExecutor.andGiven(eventsBuilderList)
        } else if (eventsBuilderList.isEmpty() && commandsBuilderList.isNotEmpty() )  {
            testExecutor.andGivenCommands(commandsBuilderList)
        }  else if (eventsBuilderList.isNotEmpty() && commandsBuilderList.isNotEmpty()) {
            testExecutor.andGiven(eventsBuilderList).andGivenCommands(commandsBuilderList)
        } else {
            testExecutor
        }
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
            var repository: EventSourcingRepository<T>? = null,
            var aggregateFactory: AggregateFactory<T>? = null,
            var annotatedCommandHandler: Any? = null
    ) {
        var commandHandlers: MutableMap<Class<*>, MessageHandler<CommandMessage<*>>> = mutableMapOf()
            private set
        var injectableResourcesBuilder: AnyUnaryBuilder = AnyUnaryBuilder()
            private set
        var commandDispatchInterceptorBuilder: RegisterCommandDispatchInterceptorBuilder = RegisterCommandDispatchInterceptorBuilder()
            private set

        class RegisterCommandDispatchInterceptorBuilder {
            val list = mutableListOf<MessageDispatchInterceptor<CommandMessage<*>>>()
            operator fun MessageDispatchInterceptor<CommandMessage<*>>.unaryPlus() {
                list.add(this)
            }
        }

        inline fun <reified C> commandHandler(handler: MessageHandler<CommandMessage<*>>) {
            addCommandHandler(C::class.java, handler)
        }

        fun <C> addCommandHandler(payloadType: Class<C>, command: MessageHandler<CommandMessage<*>>) {
            commandHandlers[payloadType] = command
        }

        fun commandDispatchInterceptors(builder: RegisterCommandDispatchInterceptorBuilder.() -> Unit) =
            commandDispatchInterceptorBuilder.apply(builder).list

        fun injectableResources(builder: AnyUnaryBuilder.() -> Unit) = injectableResourcesBuilder.apply(builder).list
    }

    data class ExpectsBuilder(
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

    class GivenBuilder {
        val eventsBuilder: AggregateTestFixtureBuilder.AnyUnaryBuilder = AggregateTestFixtureBuilder.AnyUnaryBuilder()
        val commandsBuilder: AggregateTestFixtureBuilder.AnyUnaryBuilder = AggregateTestFixtureBuilder.AnyUnaryBuilder()
        var commandsTrue: Boolean = false
        var eventsTrue: Boolean = false

        fun events(builder: AggregateTestFixtureBuilder.AnyUnaryBuilder.() -> Unit) {
            eventsTrue = true
            eventsBuilder.apply(builder).list
        }

        fun commands(builder: AggregateTestFixtureBuilder.AnyUnaryBuilder.() -> Unit) {
            commandsTrue = true
            commandsBuilder.apply(builder).list
        }
    }

    class AnyUnaryBuilder {
        val list = mutableListOf<Any>()
        operator fun Any.unaryPlus() {
            list.add(this)
        }
    }
}

