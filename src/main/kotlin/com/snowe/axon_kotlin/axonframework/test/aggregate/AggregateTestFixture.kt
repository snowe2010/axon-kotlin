package com.snowe.axon_kotlin.axonframework.test.aggregate

import org.axonframework.commandhandling.CommandMessage
import org.axonframework.messaging.MessageHandler
import org.axonframework.test.aggregate.AggregateTestFixture
import org.axonframework.test.aggregate.ResultValidator
import org.axonframework.test.aggregate.TestExecutor
import org.hamcrest.Matchers.hasProperty


inline fun <reified T> AggregateTestFixture() = org.axonframework.test.aggregate.AggregateTestFixture(T::class.java)
inline fun <reified T> AggregateTestFixture<*>.registerCommandHandler(commandHandler: MessageHandler<CommandMessage<*>>) = this.registerCommandHandler(T::class.java, commandHandler)
inline fun <reified T> AggregateTestFixture<*>.registerIgnoredField(fieldName: String) = this.registerIgnoredField(T::class.java, fieldName)
fun TestExecutor.whenever(command: Any): ResultValidator = this.`when`(command)

/**
 * Expect Exception with a reified class and a provided Exception message
 *
 * @param message the message that is expected to be part of the Exception's message
 * @receiver org.axonframework.test.aggregate.ResultValidator's fluent api
 */
inline fun <reified T : Throwable> ResultValidator.expectException(message: String) =
    this.expectException(T::class.java)
            .expectException(hasProperty<String>("message", org.hamcrest.Matchers.`is`("with a message")))

/**
 * Expect Exception with a reified class
 *
 * @receiver org.axonframework.test.aggregate.ResultValidator's fluent api
 */
inline fun <reified T : Throwable> ResultValidator.expectException() = this.expectException(T::class.java)

