package com.snowe.axon_kotlin.axonframework.test.aggregate.dsl

import com.snowe.axon_kotlin.axonframework.test.aggregate.*
import org.axonframework.eventhandling.EventMessage
import org.axonframework.test.AxonAssertionError
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * @author Tyler Thrailkill
 */

class AggregateDslTest {

    @Test
    fun `fixture allows whenever,expect`() {
        val fixture = AggregateTestFixture<StubAnnotatedAggregate>()

        val id = UUID.randomUUID()
        fixture {
            whenever {
                CreateAggregateCommand(id) to mapOf("HI" to "anything")
            }
            expect {
                events = listOf(AggregateCreatedEvent(id))
            }
        }
    }

    @Test
    fun `Fixture fails properly with whenever,expect`() {
        val fixture = AggregateTestFixture<StubAnnotatedAggregate>()

        val id = UUID.randomUUID()
        assertFailsWith<AxonAssertionError>("One of the events contained different values than expected") {
            fixture {
                whenever {
                    CreateAggregateCommand(id) to mapOf("HI" to "anything")
                }
                expect {
                    events = listOf(AggregateCreatedEvent(UUID.randomUUID()))
                }
            }
        }
    }

    @Test
    fun `dsl allows given with events`() {
        val fixture = AggregateTestFixture<StubAnnotatedAggregate>()

        val id = UUID.randomUUID()
        fixture {
            given {
                events{+AggregateCreatedEvent(id)}
                commands{+StubCommand(id)}
            }
            whenever {
                StubCommand(id)
            }
            expect {
                events = listOf(StubEvent(id))
            }
        }
    }

    @Test
    fun `dsl allows eventsMatching`() {
        val fixture = AggregateTestFixture<StubAnnotatedAggregate>()

        val id = UUID.randomUUID()
        fixture {
            given {
                events{+AggregateCreatedEvent(id)}
                commands{+StubCommand(id)}
            }
            whenever {
                StubCommand(id)
            }
            expect {
                eventsMatching = DoesMatch()
            }
        }
    }

    @Test
    fun `dsl allows eventsMatching failure`() {
        val fixture = AggregateTestFixture<StubAnnotatedAggregate>()

        val id = UUID.randomUUID()
        val exception = assertFailsWith<AxonAssertionError> {
            fixture {
                given {
                    events{+AggregateCreatedEvent(id)}
                    commands{+StubCommand(id)}
                }
                whenever {
                    StubCommand(id)
                }
                expect {
                    eventsMatching = DoesNotMatch()
                }
            }
        }
        assertTrue(exception.message!!.contains("The published events do not match the expected events"))
    }

}
