package com.snowe.axon_kotlin.axonframework.test.aggregate

import org.axonframework.test.AxonAssertionError
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertFailsWith

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
                events(AggregateCreatedEvent(id))
                commands(StubCommand(id))
            }
            whenever {
                StubCommand(id)
            }
            expect {
                events = listOf(StubEvent(id))
            }
        }
    }

}

