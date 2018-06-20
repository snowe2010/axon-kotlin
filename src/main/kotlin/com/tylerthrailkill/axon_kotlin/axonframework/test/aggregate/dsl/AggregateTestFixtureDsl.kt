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
        val expects = expectsBuilder
        var executorBuilder = aggregateTestFixture
        executorBuilder = registerBuilder.repository?.let { aggregateTestFixture.registerRepository(it) } ?: executorBuilder
        executorBuilder = registerBuilder.aggregateFactory?.let { aggregateTestFixture.registerAggregateFactory(it) } ?: executorBuilder
        executorBuilder = registerBuilder.annotatedCommandHandler?.let { aggregateTestFixture.registerAnnotatedCommandHandler(it) }
                ?: executorBuilder
        registerBuilder.commandDispatchInterceptorBuilder.list.forEach {
            aggregateTestFixture.registerCommandDispatchInterceptor(it)
        }
        registerBuilder.commandHandlers.forEach {
            executorBuilder.registerCommandHandler(it.key, it.value)
        }
        val eventsBuilderList = givenBuilder.eventsBuilder.list
        val commandsBuilderList = givenBuilder.commandsBuilder.list
        var testExecutor: TestExecutor = if (eventsBuilderList.isNotEmpty() && commandsBuilderList.isEmpty()) {
            executorBuilder.given(eventsBuilderList)
        } else if (eventsBuilderList.isEmpty() && commandsBuilderList.isNotEmpty() )  {
            executorBuilder.givenCommands(commandsBuilderList)
        }  else if (eventsBuilderList.isNotEmpty() && commandsBuilderList.isNotEmpty()) {
            executorBuilder.given(eventsBuilderList).andGivenCommands(commandsBuilderList)
        } else {
            throw RuntimeException()
        }

        var resultValidator = testExecutor.whenever(wheneverCommand!!, wheneverMetaData)
        resultValidator = expects.events?.let { resultValidator.expectEvents(*it.toTypedArray()) } ?: resultValidator
        resultValidator = expects.eventsMatching?.let { resultValidator.expectEventsMatching(it) } ?: resultValidator
        resultValidator = expects.returnValue?.let { resultValidator.expectReturnValue(it) } ?: resultValidator
        resultValidator = expects.returnValueMatching?.let { resultValidator.expectReturnValueMatching(it) } ?: resultValidator
        resultValidator = expects.exception?.let { resultValidator.expectException(it) } ?: resultValidator
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
            var events: List<Any>? = null,
            var eventsMatching: Matcher<out MutableList<in EventMessage<*>>>? = null,
            var exception: Matcher<*>? = null
    )

    class GivenBuilder {
        val eventsBuilder: GivenEventsBuilder = GivenEventsBuilder()
        val commandsBuilder: GivenCommandsBuilder = GivenCommandsBuilder()

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
            eventsBuilder.apply(builder).list
        }

        fun commands(builder: GivenCommandsBuilder.() -> Unit) {
            commandsBuilder.apply(builder).list
        }
    }

}

