package com.tylerthrailkill.axon_kotlin.axonframework.test.saga.extensions

import org.axonframework.eventhandling.saga.AssociationValue
import org.axonframework.eventhandling.saga.repository.inmemory.InMemorySagaStore
import org.axonframework.test.AxonAssertionError
import java.lang.String.format

/**
 * Helper class for the validation of Saga Repository content.
 *
 * @author Allard Buijze
 * @since 1.1
 */
class RepositoryContentValidator<T>
/**
 * Initialize the validator to validate contents of the given `sagaRepository`, which contains Sagas of
 * the given `sagaType`.
 *
 * @param sagaStore The SagaStore to monitor
 */
internal constructor(private val sagaType: Class<T>, private val sagaStore: InMemorySagaStore) {

    /**
     * Asserts that an association is present for the given `associationKey` and
     * `associationValue`.
     *
     * @param associationKey   The key of the association
     * @param associationValue The value of the association
     */
    fun assertAssociationPresent(associationKey: String, associationValue: String) {
        val associatedSagas = sagaStore.findSagas(sagaType, AssociationValue(associationKey, associationValue))
        if (associatedSagas.isEmpty()) {
            throw AxonAssertionError(format(
                    "Expected a saga to be associated with key:<%s> value:<%s>, but found <none>",
                    associationKey,
                    associationValue))
        }
    }

    /**
     * Asserts that *no* association is present for the given `associationKey` and
     * `associationValue`.
     *
     * @param associationKey   The key of the association
     * @param associationValue The value of the association
     */
    fun assertNoAssociationPresent(associationKey: String, associationValue: String) {
        val associatedSagas = sagaStore.findSagas(sagaType, AssociationValue(associationKey, associationValue))
        if (!associatedSagas.isEmpty()) {
            throw AxonAssertionError(format(
                    "Expected a saga to be associated with key:<%s> value:<%s>, but found <%s>",
                    associationKey,
                    associationValue,
                    associatedSagas.size))
        }
    }

    /**
     * Asserts that the repsitory contains the given `expected` amount of active sagas.
     *
     * @param expected The number of expected sagas.
     */
    fun assertActiveSagas(expected: Int) {
        if (expected != sagaStore.size()) {
            throw AxonAssertionError(format("Wrong number of active sagas. Expected <%s>, got <%s>.",
                    expected,
                    sagaStore.size()))
        }
    }
}
