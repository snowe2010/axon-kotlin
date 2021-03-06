package com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.dsl

import com.nhaarman.mockito_kotlin.*
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.TestEvent
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.commandhandling.GenericCommandMessage
import org.axonframework.commandhandling.model.AggregateIdentifier
import org.axonframework.commandhandling.model.AggregateLifecycle
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
        whenever(secondMockCommandDispatchInterceptor.handle(any<CommandMessage<Any>>()))
                .thenAnswer({ it -> it.arguments[0] })

        val expectedCommand = TestCommand(InterceptorAggregate.AGGREGATE_IDENTIFIER)
        val fix = fixture {
            register {
                commandDispatchInterceptors {
                    +firstMockCommandDispatchInterceptor
                    +secondMockCommandDispatchInterceptor
                }
            }
            given {
                events {
                    +StandardAggregateCreatedEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER)
                }
            }
            whenever { expectedCommand }
        }

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
        fixture {
            given {
                events {
                    +StandardAggregateCreatedEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER)
                }
            }
            whenever { TestCommand(InterceptorAggregate.AGGREGATE_IDENTIFIER) }
            expect {
                events {
                    +TestEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER, emptyMap())
                }
            }
        }

        fixture {
            register {
                commandDispatchInterceptors { +TestCommandDispatchInterceptor() }
            }
            given {
                events {
                    +StandardAggregateCreatedEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER)
                }
            }
            whenever { TestCommand(InterceptorAggregate.AGGREGATE_IDENTIFIER) }
            expect {
                events { +TestEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER, mapOf(DISPATCH_META_DATA_KEY to DISPATCH_META_DATA_VALUE)) }
            }
        }
    }

    @Test
    fun testRegisteredCommandDispatchInterceptorIsInvokedForFixtureMethodsGivenCommands() {
        fixture {
            register {
                commandDispatchInterceptors { +TestCommandDispatchInterceptor() }
            }
            given {
                commands { +CreateStandardAggregateCommand(InterceptorAggregate.AGGREGATE_IDENTIFIER) }
            }
            whenever { TestCommand(InterceptorAggregate.AGGREGATE_IDENTIFIER) }
            expect {
                events { +TestEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER, mapOf(DISPATCH_META_DATA_KEY to DISPATCH_META_DATA_VALUE)) }
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testRegisteredCommandHandlerInterceptorsAreInvoked() {
        whenever(mockCommandHandlerInterceptor.handle(any<UnitOfWork<CommandMessage<Any>>>(), any()))
                .thenAnswer { it.arguments }

        val expectedCommand = TestCommand(InterceptorAggregate.AGGREGATE_IDENTIFIER)
        val expectedMetaDataMap: HashMap<String, Any> = HashMap<String, Any>()
        expectedMetaDataMap[HANDLER_META_DATA_KEY] = HANDLER_META_DATA_VALUE

        fixture {
            register {
                commandHandlerInterceptors {
                    +TestCommandHandlerInterceptor()
                    +mockCommandHandlerInterceptor
                }
            }
            given {
                events {
                    +StandardAggregateCreatedEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER)
                }
            }
            whenever { expectedCommand to expectedMetaDataMap }
        }

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
        fixture {
            given {
                events { +StandardAggregateCreatedEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER) }
            }
            whenever { TestCommand(InterceptorAggregate.AGGREGATE_IDENTIFIER) }
            expect {
                events { +TestEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER, emptyMap()) }
            }
        }

        val expectedMetaDataMap = HashMap<String, Any>()
        expectedMetaDataMap[HANDLER_META_DATA_KEY] = HANDLER_META_DATA_VALUE
        fixture {
            register {
                commandHandlerInterceptors { +TestCommandHandlerInterceptor() }
            }
            given {
                events { +StandardAggregateCreatedEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER) }
            }
            whenever { TestCommand(InterceptorAggregate.AGGREGATE_IDENTIFIER) to expectedMetaDataMap }
            expect { events { +TestEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER, expectedMetaDataMap) } }
        }
    }

    @Test
    fun testRegisteredCommandHandlerInterceptorIsInvokedForFixtureMethodsGivenCommands() {
        val expectedMetaDataMap = HashMap<String, Any>()
        expectedMetaDataMap[HANDLER_META_DATA_KEY] = HANDLER_META_DATA_VALUE
        fixture {
            register {
                commandHandlerInterceptors { +TestCommandHandlerInterceptor() }
            }
            given {
                commands { +CreateStandardAggregateCommand(InterceptorAggregate.AGGREGATE_IDENTIFIER) }
            }
            whenever { TestCommand(InterceptorAggregate.AGGREGATE_IDENTIFIER) to expectedMetaDataMap }
            expect {
                events { +TestEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER, expectedMetaDataMap) }
            }
        }
    }

    @Test
    fun testRegisteredCommandDispatchAndHandlerInterceptorAreBothInvokedAndAlterEvent() {
        fixture {
            given {
                events { +StandardAggregateCreatedEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER) }
            }
            whenever { TestCommand(InterceptorAggregate.AGGREGATE_IDENTIFIER) }
            expect {
                events { +TestEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER, emptyMap()) }
            }
        }

        val testMetaDataMap = HashMap<String, Any>()
        testMetaDataMap[HANDLER_META_DATA_KEY] = HANDLER_META_DATA_VALUE

        val expectedMetaDataMap = HashMap(testMetaDataMap)
        expectedMetaDataMap[DISPATCH_META_DATA_KEY] = DISPATCH_META_DATA_VALUE

        fixture {
            register {
                commandDispatchInterceptors { +TestCommandDispatchInterceptor() }
                commandHandlerInterceptors { +TestCommandHandlerInterceptor() }
            }
            given {
                events { +StandardAggregateCreatedEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER) }
            }
            whenever { TestCommand(InterceptorAggregate.AGGREGATE_IDENTIFIER) to testMetaDataMap }
            expect {
                events { +TestEvent(InterceptorAggregate.AGGREGATE_IDENTIFIER, MetaData(expectedMetaDataMap)) }
            }
        }
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
            AggregateLifecycle.apply(StandardAggregateCreatedEvent(cmd.aggregateIdentifier))
        }

        @CommandHandler
        fun handle(command: TestCommand, metaData: MetaData) {
            AggregateLifecycle.apply(TestEvent(command.aggregateIdentifier, metaData))
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
