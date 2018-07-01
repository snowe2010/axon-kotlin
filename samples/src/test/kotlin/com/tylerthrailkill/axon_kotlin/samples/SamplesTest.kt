package com.tylerthrailkill.axon_kotlin.samples;

import com.nhaarman.mockito_kotlin.any
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.AggregateTestFixture
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.dsl.AggregateTestFixtureBuilder
import com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.dsl.invoke
import org.hamcrest.Matcher
import org.junit.jupiter.api.Test;
import java.util.*

/**
 * @author Tyler Thrailkill
 */

class SamplesTest {

    val id: UUID = UUID.randomUUID()

    @Test
    fun showConstructorUsage() {
        val fixture = AggregateTestFixture<StubAnnotatedAggregate>()
    }

    @Test
    fun reportIllegalStateChange() {
        val fixture = AggregateTestFixture<StubAnnotatedAggregate>()
        fixture {
            reportIllegalStateChange = true
        }
    }

    @Test
    fun showRetrievingTestExecutor() {
        val fixture = AggregateTestFixture<StubAnnotatedAggregate>()
        fixture {
        }.testExecutor
    }

    @Test
    fun registerBlock() {
        val fixture = AggregateTestFixture<StubAnnotatedAggregate>()
        fixture {
            register {
            }
        }
    }

    @Test
    fun givenBlock() {
        val fixture = AggregateTestFixture<StubAnnotatedAggregate>()
        fixture {
            given {
            }
        }
    }

    @Test
    fun wheneverBlock() {
        val fixture = AggregateTestFixture<StubAnnotatedAggregate>()
        fixture {
            whenever {
            }
        }
    }

    @Test
    fun wheneverBlockWithCommand() {
        val fixture = AggregateTestFixture<StubAnnotatedAggregate>()
        fixture {
            whenever {
                CreateStubAggregateCommand(id)
            }
        }
    }

    @Test
    fun wheneverBlockWithCommandAndMetaData() {
        val fixture = AggregateTestFixture<StubAnnotatedAggregate>()
        fixture {
            whenever {
                CreateStubAggregateCommand(id) to mapOf("String" to "anything")
            }
        }
    }

    @Test
    fun expectExample() {
        val fixture = AggregateTestFixture<StubAnnotatedAggregate>()
        fixture {
            expect {

            }
        }
    }

    @Test
    fun expectShowAllMethodsExample() {
        val fixture = AggregateTestFixture<StubAnnotatedAggregate>()
        fixture {
            expect {
                returnValue = ""
                returnValueMatching = any()
                noEvents = true
                eventsMatching = any()
                events {
                    +AggregateCreatedEvent(id)
                }
                successfulHandlerExecution()
                exception<RuntimeException>()
                exception(any())
            }
        }
    }

    @Test
    fun anyUnaryBuilderExample() {
        AggregateTestFixtureBuilder.AnyUnaryBuilder().apply {
            +"Item1"
            +1
            +true
            +RuntimeException("")
            +0.1
        }
    }
}