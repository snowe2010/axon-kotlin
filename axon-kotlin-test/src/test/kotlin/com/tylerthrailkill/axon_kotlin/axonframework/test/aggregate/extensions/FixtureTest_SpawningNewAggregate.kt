package com.tylerthrailkill.axon_kotlin.axonframework.test.aggregate.extensions


/**
 * Fixture tests for spawning new aggregate functionality.
 *
 * @author Milan Savic
 */
/*
class FixtureTest_SpawningNewAggregate {

    private lateinit var fixture: AggregateTestFixture<Aggregate1>

    @BeforeEach
    fun setUp() {
        fixture = AggregateTestFixture(Aggregate1::class.java)
    }

    @Test
    fun testFixtureWithoutRepositoryProviderInjected() {
        fixture.givenNoPriorActivity()
                .whenever(CreateAggregate1Command("id", "aggregate2Id"))
                .expectEvents(Aggregate2CreatedEvent("aggregate2Id"), Aggregate1CreatedEvent("id"))
                .expectSuccessfulHandlerExecution()
    }

    @Test
    @Throws(Exception::class)
    fun testFixtureWithRepositoryProviderInjected() {
        val repositoryProvider = mock<RepositoryProvider>()
        val aggregate2Repository = mock<Repository<Aggregate2>>()
        val aggregate2Model = AnnotatedAggregateMetaModelFactory
                .inspectAggregate(Aggregate2::class.java)

        whenever(aggregate2Repository.newInstance(any<Callable<Aggregate2>>())).thenAnswer({ invocation ->
            EventSourcedAggregate
                    .initialize(invocation
                            .arguments[0] as Callable<Aggregate2>,
                            aggregate2Model,
                            fixture.eventStore,
                            repositoryProvider)
        })

        whenever(repositoryProvider.repositoryFor(Aggregate2::class.java)).thenReturn(aggregate2Repository)

        fixture.registerRepositoryProvider(repositoryProvider)
                .givenNoPriorActivity()
                .whenever(CreateAggregate1Command("id", "aggregate2Id"))
                .expectEvents(Aggregate2CreatedEvent("aggregate2Id"), Aggregate1CreatedEvent("id"))
                .expectSuccessfulHandlerExecution()

        verify(repositoryProvider).repositoryFor(Aggregate2::class.java)
        verify(aggregate2Repository).newInstance(any<Callable<Aggregate2>>())
    }

    private data class CreateAggregate1Command(val id: String, val aggregate2Id: String)

    private data class Aggregate1CreatedEvent(val id: String)

    private data class Aggregate2CreatedEvent(val id: String)

    private class Aggregate1 {

        @AggregateIdentifier
        private var id: String? = null

        constructor() {}

        @CommandHandler
        @Throws(Exception::class)
        constructor(command: CreateAggregate1Command) {
            apply(Aggregate1CreatedEvent(command.id))
            createNew(Aggregate2::class.java) { Aggregate2(command.aggregate2Id) }
        }

        @EventSourcingHandler
        @Throws(Exception::class)
        fun on(event: Aggregate1CreatedEvent) {
            this.id = event.id
        }
    }

    private class Aggregate2 {

        @AggregateIdentifier
        private var id: String? = null
        private val state: String? = null

        constructor() {}

        constructor(id: String) {
            apply(Aggregate2CreatedEvent(id))
        }

        @EventSourcingHandler
        fun on(event: Aggregate2CreatedEvent) {
            this.id = event.id
        }
    }
}
*/
