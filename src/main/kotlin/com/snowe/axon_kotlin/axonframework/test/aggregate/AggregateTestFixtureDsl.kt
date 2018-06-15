package com.snowe.axon_kotlin.axonframework.test.aggregate

import org.axonframework.messaging.MetaData
import org.axonframework.test.aggregate.AggregateTestFixture
import kotlin.collections.ArrayList


operator fun <T> AggregateTestFixture<T>.invoke(init: AggregateTestFixtureBuilder<T>.() -> Unit): AggregateTestFixture<T> {
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
class AggregateTestFixtureBuilder<T> {
    val aggregateTestFixture: AggregateTestFixture<T>
    var wheneverCommand: Any? = null
    var wheneverMetaData: Map<String, *> = MetaData.emptyInstance()
    private var expectsBuilder: ExpectsBuilder = ExpectsBuilder()
    private var givenBuilder: GivenBuilder = GivenBuilder()

    constructor(aggregateTestFixture: AggregateTestFixture<T>) {
        this.aggregateTestFixture = aggregateTestFixture
    }

    fun given(block: GivenBuilder.() -> Unit) = givenBuilder.apply(block)

    fun whenever(block: () -> Any) {
        val out = block()
        when (out) {
            is Pair<*, *> -> {
                wheneverCommand = out.first
                wheneverMetaData = out.second as Map<String, *>
            }
            else -> {
                wheneverCommand = out
            }
        }
    }

    fun expect(block: ExpectsBuilder.() -> Unit) = expectsBuilder.apply(block)

    fun build() {
        requireNotNull(wheneverCommand)
        requireNotNull(expectsBuilder)
        val expects = expectsBuilder

        val testExecutor = aggregateTestFixture.given(givenBuilder.givenEvents).andGivenCommands(givenBuilder.givenCommands)
        val resultValidator = testExecutor.whenever(wheneverCommand!!)

        resultValidator.expectEvents(*expects.events.toTypedArray())
    }
}

data class GivenBuilder(val givenEvents: ArrayList<Any> = arrayListOf(),
                        val givenCommands: ArrayList<Any> = arrayListOf()) {

    fun events(vararg event: Any) {
        event.filterNotNull().forEach { givenEvents.add(it) }
    }

    fun commands(vararg command: Any) {
        command.filterNotNull().forEach { givenCommands.add(it) }
    }
}


class ExpectsBuilder {
    var returnValue: Any? = null
    var noEvents: Boolean? = null
    lateinit var events: List<Any>
}
