package com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions

import com.nhaarman.mockito_kotlin.*
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.TestEvent
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.dsl.MyEntity
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.dsl.TestCommand
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.whenever
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.commandhandling.GenericCommandMessage
import org.axonframework.commandhandling.model.AggregateIdentifier
import org.axonframework.commandhandling.model.AggregateLifecycle.apply
import org.axonframework.eventhandling.EventHandler
import org.axonframework.messaging.InterceptorChain
import org.axonframework.messaging.MessageDispatchInterceptor
import org.axonframework.messaging.MessageHandlerInterceptor
import org.axonframework.messaging.MetaData
import org.axonframework.messaging.correlation.SimpleCorrelationDataProvider
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.axonframework.test.aggregate.AggregateTestFixture
import org.axonframework.test.aggregate.FixtureConfiguration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import java.util.function.BiFunction
import kotlin.collections.HashMap
import kotlin.test.assertEquals


class FixtureTest_CommandInterceptors {

    private lateinit var fixture: FixtureConfiguration<InterceptorAggregate>

    private val firstMockCommandDispatchInterceptor = mock<MessageDispatchInterceptor<CommandMessage<*>>>()
    private val secondMockCommandDispatchInterceptor = mock<MessageDispatchInterceptor<CommandMessage<*>>>()
    private val mockCommandHandlerInterceptor = mock<MessageHandlerInterceptor<CommandMessage<*>>>()

    @BeforeEach
    fun setUp() {
        fixture = AggregateTestFixture(InterceptorAggregate::class.java)
    }

    @Test
    fun testRegisteredCommandDispatchInterceptorsAreInvoked() {
        whenever(firstMockCommandDispatchInterceptor.handle(any<CommandMessage<Any>>()))
                .thenAnswer({ it -> it.arguments[0] })
        fixture.registerCommandDispatchInterceptor(firstMockCommandDispatchInterceptor)
        whenever(secondMockCommandDispatchInterceptor.handle(any<CommandMessage<Any>>()))
                .thenAnswer({ it -> it.arguments[0] })
        fixture.registerCommandDispatchInterceptor(secondMockCommandDispatchInterceptor)

        val expectedCommand = TestCommand(InterceptorAggregate.AGGREGATE_IDENTIFIER)
        fixture.given(StandardAggregateCreatedEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER))
                .whenever(expectedCommand)

        val firstCommandMessageCaptor = argumentCaptor<GenericCommandMessage<Any>>()
        verify(firstMockCommandDispatchInterceptor).handle(firstCommandMessageCaptor.capture())
        val firstResult = firstCommandMessageCaptor.firstValue
        assertEquals(expectedCommand, firstResult.payload)

        val secondCommandMessageCaptor = argumentCaptor<GenericCommandMessage<Any>>()
        verify(secondMockCommandDispatchInterceptor).handle(secondCommandMessageCaptor.capture())
        val secondResult = secondCommandMessageCaptor.firstValue
        assertEquals(expectedCommand, secondResult.payload)
    }

    @Test
    fun testRegisteredCommandDispatchInterceptorIsInvokedAndAltersAppliedEvent() {
        fixture.given(StandardAggregateCreatedEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER))
                .whenever(TestCommand(InterceptorAggregate.AGGREGATE_IDENTIFIER))
                .expectEvents(TestEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER, emptyMap()))

        fixture.registerCommandDispatchInterceptor(TestCommandDispatchInterceptor())

        val expectedValues = MetaData(Collections.singletonMap(DISPATCH_META_DATA_KEY, DISPATCH_META_DATA_VALUE))

        fixture.given(StandardAggregateCreatedEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER))
                .whenever(TestCommand(InterceptorAggregate.AGGREGATE_IDENTIFIER))
                .expectEvents(TestEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER, expectedValues))
    }

    @Test
    fun testRegisteredCommandDispatchInterceptorIsInvokedForFixtureMethodsGivenCommands() {
        fixture.registerCommandDispatchInterceptor(TestCommandDispatchInterceptor())

        val expectedValues = MetaData(Collections.singletonMap(DISPATCH_META_DATA_KEY, DISPATCH_META_DATA_VALUE))

        fixture.givenCommands(CreateStandardAggregateCommand(InterceptorAggregate.AGGREGATE_IDENTIFIER))
                .whenever(TestCommand(InterceptorAggregate.AGGREGATE_IDENTIFIER))
                .expectEvents(TestEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER, expectedValues))
    }

    @Test
    @Throws(Exception::class)
    fun testRegisteredCommandHandlerInterceptorsAreInvoked() {
        fixture.registerCommandHandlerInterceptor(TestCommandHandlerInterceptor())
        whenever(mockCommandHandlerInterceptor.handle(any<UnitOfWork<CommandMessage<Any>>>(), any()))
                .thenAnswer({ it.arguments })
        fixture.registerCommandHandlerInterceptor(mockCommandHandlerInterceptor)

        val expectedCommand = TestCommand(InterceptorAggregate.AGGREGATE_IDENTIFIER)
        val expectedMetaDataMap: HashMap<String, Any> = HashMap<String, Any>()
        expectedMetaDataMap[HANDLER_META_DATA_KEY] = HANDLER_META_DATA_VALUE

        fixture.given(StandardAggregateCreatedEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER))
                .whenever(expectedCommand, expectedMetaDataMap)

        val unitOfWorkCaptor = argumentCaptor<UnitOfWork<CommandMessage<Any>>>()
        val interceptorChainCaptor = argumentCaptor<InterceptorChain>()
        verify(mockCommandHandlerInterceptor).handle(unitOfWorkCaptor.capture(), interceptorChainCaptor.capture())
        val unitOfWorkResult = unitOfWorkCaptor.firstValue
        val messageResult = unitOfWorkResult.message
        assertEquals(expectedCommand, messageResult.payload)
        assertEquals(expectedMetaDataMap, messageResult.metaData as Map<String, Any>)
    }

    @Test
    fun testRegisteredCommandHandlerInterceptorIsInvokedAndAltersEvent() {
        fixture.given(StandardAggregateCreatedEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER))
                .whenever(TestCommand(InterceptorAggregate.AGGREGATE_IDENTIFIER))
                .expectEvents(TestEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER, emptyMap()))

        fixture.registerCommandHandlerInterceptor(TestCommandHandlerInterceptor())

        val expectedMetaDataMap = HashMap<String, Any>()
        expectedMetaDataMap[HANDLER_META_DATA_KEY] = HANDLER_META_DATA_VALUE

        fixture.given(StandardAggregateCreatedEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER))
                .whenever(TestCommand(InterceptorAggregate.AGGREGATE_IDENTIFIER), expectedMetaDataMap)
                .expectEvents(TestEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER, expectedMetaDataMap))
    }

    @Test
    fun testRegisteredCommandHandlerInterceptorIsInvokedForFixtureMethodsGivenCommands() {
        fixture.registerCommandHandlerInterceptor(TestCommandHandlerInterceptor())

        val expectedMetaDataMap = HashMap<String, Any>()
        expectedMetaDataMap[HANDLER_META_DATA_KEY] = HANDLER_META_DATA_VALUE

        fixture.givenCommands(CreateStandardAggregateCommand(InterceptorAggregate.AGGREGATE_IDENTIFIER))
                .whenever(TestCommand(InterceptorAggregate.AGGREGATE_IDENTIFIER), expectedMetaDataMap)
                .expectEvents(TestEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER, expectedMetaDataMap))
    }

    @Test
    fun testRegisteredCommandDispatchAndHandlerInterceptorAreBothInvokedAndAlterEvent() {
        fixture.given(StandardAggregateCreatedEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER))
                .whenever(TestCommand(InterceptorAggregate.AGGREGATE_IDENTIFIER))
                .expectEvents(TestEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER, emptyMap()))

        fixture.registerCommandDispatchInterceptor(TestCommandDispatchInterceptor())
        fixture.registerCommandHandlerInterceptor(TestCommandHandlerInterceptor())

        val testMetaDataMap = HashMap<String, Any>()
        testMetaDataMap[HANDLER_META_DATA_KEY] = HANDLER_META_DATA_VALUE

        val expectedMetaDataMap = HashMap(testMetaDataMap)
        expectedMetaDataMap[DISPATCH_META_DATA_KEY] = DISPATCH_META_DATA_VALUE

        fixture.given(StandardAggregateCreatedEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER))
                .whenever(TestCommand(InterceptorAggregate.AGGREGATE_IDENTIFIER), testMetaDataMap)
                .expectEvents(TestEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER, MetaData(expectedMetaDataMap)))
    }

    private class InterceptorAggregate {

        @Transient
        private val counter: Int = 0
        private val lastNumber: Int? = null
        @AggregateIdentifier
        private var identifier: String? = null
        private val entity: MyEntity? = null

        constructor() {}

        constructor(aggregateIdentifier: Any) {
            identifier = aggregateIdentifier.toString()
        }

        @CommandHandler
        constructor(cmd: CreateStandardAggregateCommand) {
            apply(StandardAggregateCreatedEvent(cmd.aggregateIdentifier))
        }

        @CommandHandler
        fun handle(command: TestCommand, metaData: MetaData) {
            apply(TestEvent(command.aggregateIdentifier, metaData))
        }

        @EventHandler
        fun handle(event: StandardAggregateCreatedEvent) {
            this.identifier = event.aggregateIdentifier.toString()
        }

        companion object {
            val AGGREGATE_IDENTIFIER = "id1"
        }

    }

    private class CreateStandardAggregateCommand(val aggregateIdentifier: Any)

    private class StandardAggregateCreatedEvent(val aggregateIdentifier: Any)

    internal inner class TestCommandDispatchInterceptor : MessageDispatchInterceptor<CommandMessage<*>> {

        override fun handle(messages: List<CommandMessage<*>>): BiFunction<Int, CommandMessage<*>, CommandMessage<*>> {
            return BiFunction { _, message ->
                val testMetaDataMap = HashMap<String, Any>()
                testMetaDataMap[DISPATCH_META_DATA_KEY] = DISPATCH_META_DATA_VALUE
                message.andMetaData(testMetaDataMap)
            }
        }

    }

    internal inner class TestCommandHandlerInterceptor : MessageHandlerInterceptor<CommandMessage<*>> {

        @Throws(Exception::class)
        override fun handle(unitOfWork: UnitOfWork<out CommandMessage<*>>, interceptorChain: InterceptorChain): Any? {
            unitOfWork.registerCorrelationDataProvider(SimpleCorrelationDataProvider(HANDLER_META_DATA_KEY))
            return interceptorChain.proceed()
        }
    }

    companion object {

        private val DISPATCH_META_DATA_KEY = "dispatchKey"
        private val DISPATCH_META_DATA_VALUE = "dispatchValue"
        private val HANDLER_META_DATA_KEY = "handlerKey"
        private val HANDLER_META_DATA_VALUE = "handlerValue"
    }

}
