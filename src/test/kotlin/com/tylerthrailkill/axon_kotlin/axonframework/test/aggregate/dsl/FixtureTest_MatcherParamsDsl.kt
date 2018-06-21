package com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.dsl

import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.AggregateTestFixture
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.eventhandling.EventMessage
import org.axonframework.messaging.MessageHandler
import org.axonframework.test.AxonAssertionError
import org.axonframework.test.aggregate.FixtureConfiguration
import org.axonframework.test.matchers.Matchers.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Heavily borrowed from AxonFramework will permission from Allard Buize and Steven Van Beelen
 *
 */
class FixtureTest_MatcherParamsDsl {
    private lateinit var fixture: FixtureConfiguration<StandardAggregate>

    @BeforeEach
    fun beforeAll() {
        fixture = AggregateTestFixture<StandardAggregate>()
        fixture.registerAggregateFactory(StandardAggregate.Factory())
    }

    @Test
    fun testFirstFixture() {
        fixture {
            register {
                annotatedCommandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
            }
            given {
                events { +MyEvent("aggregateId", 1) }
            }
            whenever { TestCommand("aggregateId") }
            expect {
                returnValueMatching = DoesMatch<Any>()
                eventsMatching = sequenceOf<EventMessage<*>>(matches<EventMessage<*>> { true })
            }
        }
    }

    @Test
    fun testPayloadsMatch() {
        fixture {
            register {
                annotatedCommandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
            }
            given {
                events { +MyEvent("aggregateId", 1)}
            }
            whenever { TestCommand("aggregateId") }
            expect {
                returnValueMatching = DoesMatch<Any>()
                eventsMatching = payloadsMatching(sequenceOf<Any>(matches<Any> { true }))
            }
        }
    }

    @Test
    fun testPayloadsMatchExact() {
        fixture {
            register {
                annotatedCommandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
            }
            given {
                events{+MyEvent("aggregateId", 1)}
            }
            whenever { TestCommand("aggregateId") }
            expect {
                returnValueMatching = DoesMatch<Any>()
                eventsMatching = payloadsMatching(exactSequenceOf(matches<Any> { true }))
            }
        }
    }

    @Test
    fun testPayloadsMatchPredicate() {
        fixture {
            register {
                annotatedCommandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
            }
            given {
                events{+MyEvent("aggregateId", 1)}
            }
            whenever { TestCommand("aggregateId") }
            expect {
                returnValueMatching = DoesMatch<Any>()
                eventsMatching = payloadsMatching(predicate { ml -> !ml.isEmpty() })
            }
        }
    }

    @Test
    fun testFixture_UnexpectedException() {
        try {
            fixture {
                register {
                    annotatedCommandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
                }
                given {
                    events {
                        +MyEvent("aggregateId", 1)
                        +MyEvent("aggregateId", 2)
                        +MyEvent("aggregateId", 3)
                    }
                }
                whenever { StrangeCommand("aggregateId") }
                expect {
                    returnValueMatching = DoesMatch<Any>()
                }
            }
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("but got <exception of type [StrangeCommandReceivedException]>"))
        }
    }

    @Test
    fun testFixture_UnexpectedReturnValue() {
        try {
            fixture {
                register {
                    annotatedCommandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
                }
                given {
                    events {
                        +MyEvent("aggregateId", 1)
                        +MyEvent("aggregateId", 2)
                        +MyEvent("aggregateId", 3)
                    }
                }
                whenever { TestCommand("aggregateId") }
                expect {
                    exception = DoesMatch<Any>()
                }
            }
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("The command handler returned normally, but an exception was expected"))
            assertTrue(e.message!!.contains("<anything> but returned with <null>"))
        }
    }

    @Test
    fun testFixture_WrongReturnValue() {
        try {
            fixture {
                register {
                    annotatedCommandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
                }
                given {
                    events {
                        +MyEvent("aggregateId", 1)
                        +MyEvent("aggregateId", 2)
                        +MyEvent("aggregateId", 3)
                    }
                }
                whenever { TestCommand("aggregateId") }
                expect {
                    returnValueMatching = DoesNotMatch<Any>()
                }
            }
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("<something you can never give me> but got <null>"))
        }

    }

    @Test
    fun testFixture_WrongExceptionType() {
        try {
            fixture {
                register {
                    annotatedCommandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
                }
                given {
                    events {
                        +MyEvent("aggregateId", 1)
                        +MyEvent("aggregateId", 2)
                        +MyEvent("aggregateId", 3)
                    }
                }
                whenever { StrangeCommand("aggregateId") }
                expect {
                    exception = DoesNotMatch<Any>()
                }
            }
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains(
                    "<something you can never give me> but got <exception of type [StrangeCommandReceivedException]>"))
        }

    }

    @Test
    fun testFixture_ExpectedPublishedSameAsStored() {
        try {
            fixture {
                register {
                    annotatedCommandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
                }
                given {
                    events {
                        +MyEvent("aggregateId", 1)
                        +MyEvent("aggregateId", 2)
                        +MyEvent("aggregateId", 3)
                    }
                }
                whenever { StrangeCommand("aggregateId") }
                expect {
                    events {
                        +DoesMatch<List<EventMessage<*>>>()
                    }
                }
            }
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("The published events do not match the expected events"))
            assertTrue(e.message!!.contains("com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.dsl.DoesMatch <|> "))
            assertTrue(e.message!!.contains("probable cause"))
        }
    }

    @Test
    @Throws(Exception::class)
    fun testFixture_DispatchMetaDataInCommand() {
        val mockCommandHandler = mock<MessageHandler<CommandMessage<*>>>()

        fixture {
            register {
                annotatedCommandHandler = mockCommandHandler
                commandHandler<StrangeCommand>(mockCommandHandler)
            }
            given {
                events {
                    +MyEvent("aggregateId", 1)
                    +MyEvent("aggregateId", 2)
                    +MyEvent("aggregateId", 3)
                }
            }
            whenever { StrangeCommand("aggregateId") to mapOf("meta" to "value") }
        }

        val captor = argumentCaptor<CommandMessage<*>>()
        verify(mockCommandHandler).handle(captor.capture())
        val dispatched = captor.allValues
        assertEquals(1, dispatched.size)
        assertEquals(1, dispatched[0].metaData.size)
        assertEquals("value", dispatched[0].metaData["meta"])
    }

    @Test
    fun testFixture_EventDoesNotMatch() {
        try {
            fixture {
                register {
                    annotatedCommandHandler = MyCommandHandler(fixture.repository, fixture.eventBus)
                }
                given {
                    events {
                        +MyEvent("aggregateId", 1)
                        +MyEvent("aggregateId", 2)
                        +MyEvent("aggregateId", 3)
                    }
                }
                whenever { TestCommand("aggregateId") }
                expect {
                    eventsMatching = DoesNotMatch<MutableList<in EventMessage<*>>>()
                }
            }
            fail("Expected an AxonAssertionError")
        } catch (e: AxonAssertionError) {
            assertTrue(e.message!!.contains("something you can never give me"), "Wrong message: " + e.message)
        }
    }

}