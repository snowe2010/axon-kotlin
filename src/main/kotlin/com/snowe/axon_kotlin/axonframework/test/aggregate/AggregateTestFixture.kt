package com.snowe.axon_kotlin.axonframework.test.aggregate

import org.axonframework.commandhandling.CommandMessage
import org.axonframework.messaging.MessageHandler
import org.axonframework.test.aggregate.AggregateTestFixture


inline fun <reified T> AggregateTestFixture() = org.axonframework.test.aggregate.AggregateTestFixture(T::class.java)
inline fun <reified T> AggregateTestFixture<*>.registerCommandHandler(commandHandler: MessageHandler<CommandMessage<*>>) = this.registerCommandHandler(T::class.java, commandHandler)
inline fun <reified T> AggregateTestFixture<*>.registerIgnoredField(fieldName: String) = this.registerIgnoredField(T::class.java, fieldName)
