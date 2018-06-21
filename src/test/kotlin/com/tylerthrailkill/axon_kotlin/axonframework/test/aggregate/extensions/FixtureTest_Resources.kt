package com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions

import com.nhaarman.mockito_kotlin.isA
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import org.axonframework.commandhandling.CommandBus
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.commandhandling.model.AggregateIdentifier
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.test.aggregate.AggregateTestFixture
import org.axonframework.test.aggregate.FixtureConfiguration
import java.util.concurrent.Executor
import org.axonframework.commandhandling.model.AggregateLifecycle.apply
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.fail


/**
 * @author Allard Buijze
 */
class FixtureTest_Resources {

    private var fixture: FixtureConfiguration<AggregateWithResources>? = null

    @BeforeEach
    fun setUp() {
        fixture = AggregateTestFixture(AggregateWithResources::class.java)
    }

    @Test
    fun testResourcesAreScopedToSingleTest_ConstructorPartOne() {
        // executing the same test should pass, as resources are scoped to a single test only
        val resource = mock<Executor>()
        fixture!!.registerInjectableResource(resource)
                .given()
                .`when`(CreateAggregateCommand("id"))

        verify<Executor>(resource).execute(isA<Runnable>())
        verifyNoMoreInteractions(resource)
    }

    @Test
    fun testResourcesAreScopedToSingleTest_ConstructorPartTwo() {
        testResourcesAreScopedToSingleTest_ConstructorPartOne()
    }

    @Test
    fun testResourcesAreScopedToSingleTest_MethodPartOne() {
        // executing the same test should pass, as resources are scoped to a single test only
        val resource = mock<Executor>()
        fixture!!.registerInjectableResource(resource)
                .given(MyEvent("id", 1))
                .`when`(TestCommand("id"))
                .expectReturnValue(fixture!!.commandBus)

        verify<Executor>(resource).execute(isA<Runnable>())
        verifyNoMoreInteractions(resource)
    }

    @Test
    fun testResourcesAreScopedToSingleTest_MethodPartTwo() {
        testResourcesAreScopedToSingleTest_MethodPartOne()
    }

    internal class AggregateWithResources {

        @AggregateIdentifier
        private var id: String? = null

        @CommandHandler
        constructor(cmd: CreateAggregateCommand, resource: Executor, commandBus: CommandBus) {
            apply(MyEvent(cmd.aggregateIdentifier, 1))
            resource.execute { fail("Should not really be executed") }
        }

        @CommandHandler
        fun handleCommand(cmd: TestCommand, resource: Executor, commandBus: CommandBus): CommandBus {
            resource.execute { fail("Should not really be executed") }
            return commandBus
        }

        @EventSourcingHandler
        internal fun handle(event: MyEvent, resource: Executor) {
            assertNotNull(resource)
            this.id = event.aggregateIdentifier.toString()
        }

        constructor() {}
    }
}
