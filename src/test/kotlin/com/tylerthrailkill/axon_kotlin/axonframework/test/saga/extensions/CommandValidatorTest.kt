package com.tylerthrailkill.axon_kotlin.axonframework.test.saga.extensions


import org.axonframework.commandhandling.CommandMessage
import org.axonframework.commandhandling.GenericCommandMessage
import org.axonframework.test.AxonAssertionError
import org.axonframework.test.matchers.AllFieldsFilter
import org.axonframework.test.saga.CommandValidator
import org.axonframework.test.utils.RecordingCommandBus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import kotlin.test.assertFailsWith

class CommandValidatorTest {

    private var testSubject: CommandValidator? = null

    private var commandBus: RecordingCommandBus? = null

    @BeforeEach
    fun setUp() {
        commandBus = mock(RecordingCommandBus::class.java)
        testSubject = CommandValidator(commandBus, AllFieldsFilter.instance())
    }

    @Test
    fun testAssertEmptyDispatchedEqualTo() {
        `when`(commandBus!!.dispatchedCommands).thenReturn(emptyCommandMessageList())

        testSubject!!.assertDispatchedEqualTo()
    }

    @Test
    fun testAssertNonEmptyDispatchedEqualTo() {
        `when`(commandBus!!.dispatchedCommands).thenReturn(listOfOneCommandMessage("command"))

        testSubject!!.assertDispatchedEqualTo("command")
    }

    @Test
    fun testMatchWithUnexpectedNullValue() {
        assertFailsWith<AxonAssertionError> {
            `when`(commandBus!!.dispatchedCommands).thenReturn(listOfOneCommandMessage(SomeCommand(null)))

            testSubject!!.assertDispatchedEqualTo(SomeCommand("test"))
        }
    }

    private fun emptyCommandMessageList(): List<CommandMessage<*>> {
        return emptyList()
    }

    private fun listOfOneCommandMessage(msg: Any): List<CommandMessage<*>> {
        return listOf<CommandMessage<*>>(GenericCommandMessage.asCommandMessage<Any>(msg))
    }


    private inner class SomeCommand(val value: Any?)
}
