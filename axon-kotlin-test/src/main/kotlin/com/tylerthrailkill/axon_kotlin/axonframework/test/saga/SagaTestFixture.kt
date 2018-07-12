package com.tylerthrailkill.axon_kotlin.axonframework.test.saga

import org.axonframework.test.saga.SagaTestFixture

/**
 * @author Tyler Thrailkill
 */

inline fun <reified T> SagaTestFixture() = org.axonframework.test.saga.SagaTestFixture(T::class.java)
inline fun <reified T> SagaTestFixture<*>.registerCommandGateway() = this.registerCommandGateway(T::class.java)
inline fun <reified T> SagaTestFixture<*>.registerCommandGateway(stubImpl: T) = this.registerCommandGateway(T::class.java, stubImpl)
inline fun <reified T> SagaTestFixture<*>.registerIgnoredField(fieldName: String) = this.registerIgnoredField(T::class.java, fieldName)
