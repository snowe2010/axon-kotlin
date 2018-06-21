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
        return resultValidator
    }

    data class RegisterBuilder<T>(
            var repository: EventSourcingRepository<T>? = null,
            var aggregateFactory: AggregateFactory<T>? = null,
            var annotatedCommandHandler: Any? = null,
            var commandHandlers: MutableMap<Class<*>, MessageHandler<CommandMessage<*>>> = mutableMapOf(),
            var commandDispatchInterceptorBuilder: RegisterCommandDispatchInterceptorBuilder = RegisterCommandDispatchInterceptorBuilder()
    ) {

        inline fun <reified C> commandHandler(command: MessageHandler<CommandMessage<*>>) {
            addCommandHandler(C::class.java, command)
        }

        fun <C> addCommandHandler(payloadType: Class<C>, command: MessageHandler<CommandMessage<*>>) {
            commandHandlers[payloadType] = command
        }


        class RegisterCommandDispatchInterceptorBuilder {
            val list = mutableListOf<MessageDispatchInterceptor<CommandMessage<*>>>()
            operator fun MessageDispatchInterceptor<CommandMessage<*>>.unaryPlus() {
                list.add(this)
            }
        }

        fun commandDispatchInterceptors(builder: RegisterCommandDispatchInterceptorBuilder.() -> Unit) =
            commandDispatchInterceptorBuilder.apply(builder).list
    }

    data class ExpectsBuilder(
            var returnValue: Any? = null,
            var returnValueMatching: Matcher<*>? = null,
            var noEvents: Boolean? = null,
            var eventsMatching: Matcher<out MutableList<in EventMessage<*>>>? = null,
            var exception: Matcher<*>? = null
    ) {
        val eventsBuilder: ExpectEventsBuilder = ExpectEventsBuilder()
        val commandsBuilder: ExpectCommandsBuilder = ExpectCommandsBuilder()

        var commandsSet: Boolean = false
        var eventsSet: Boolean = false

        class ExpectEventsBuilder {
            val list = mutableListOf<Any>()
            operator fun Any.unaryPlus() {
                list.add(this)
            }
        }

        class ExpectCommandsBuilder {
            val list = mutableListOf<Any>()
            operator fun Any.unaryPlus() {
                list.add(this)
            }
        }

        fun events(builder: ExpectEventsBuilder.() -> Unit) {
            eventsSet = true
            eventsBuilder.apply(builder).list
        }

        fun commands(builder: ExpectCommandsBuilder.() -> Unit) {
            commandsSet = true
            commandsBuilder.apply(builder).list
        }
    }

    class GivenBuilder {
        val eventsBuilder: GivenEventsBuilder = GivenEventsBuilder()
        val commandsBuilder: GivenCommandsBuilder = GivenCommandsBuilder()
        var commandsTrue: Boolean = false
        var eventsTrue: Boolean = false

        class GivenEventsBuilder {
            val list = mutableListOf<Any>()
            operator fun Any.unaryPlus() {
                list.add(this)
            }
        }

        class GivenCommandsBuilder {
            val list = mutableListOf<Any>()
            operator fun Any.unaryPlus() {
                list.add(this)
            }
        }

        fun events(builder: GivenEventsBuilder.() -> Unit) {
            eventsTrue = true
            eventsBuilder.apply(builder).list
        }

        fun commands(builder: GivenCommandsBuilder.() -> Unit) {
            commandsTrue = true
            commandsBuilder.apply(builder).list
        }
    }

}

