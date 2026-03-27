package sollecitom.libs.lattice.sdk.kotlin.test.specification

import assertk.Assert
import assertk.assertions.support.expected
import assertk.assertions.support.show
import sollecitom.libs.lattice.sdk.kotlin.api.CommandOutcome
import sollecitom.libs.lattice.sdk.kotlin.api.CommandResponse
import sollecitom.libs.lattice.sdk.kotlin.api.QueryOutcome

fun Assert<CommandOutcome>.wasProcessed() = given { actual ->
    if (actual !is CommandOutcome.Processed) expected("to be Processed but was ${show(actual)}")
}

fun Assert<CommandOutcome>.wasRejected() = given { actual ->
    if (actual !is CommandOutcome.Rejected) expected("to be Rejected but was ${show(actual)}")
}

fun Assert<CommandOutcome>.wasRejectedWithReason(): Assert<String> = transform("rejection reason") { actual ->
    when (actual) {
        is CommandOutcome.Rejected -> actual.reason
        is CommandOutcome.Processed -> expected("to be Rejected but was Processed")
    }
}

fun <RESPONSE : Any> Assert<CommandResponse<RESPONSE>>.wasRespondedWith(): Assert<RESPONSE> = transform("response") { actual ->
    when (actual) {
        is CommandResponse.Responded -> actual.response
        is CommandResponse.Rejected -> expected("to have Responded but was Rejected with reason: ${show(actual.reason)}")
    }
}

@JvmName("commandResponseWasRejected")
fun <RESPONSE : Any> Assert<CommandResponse<RESPONSE>>.wasRejected() = given { actual ->
    if (actual !is CommandResponse.Rejected) expected("to be Rejected but was ${show(actual)}")
}

fun <ANSWER> Assert<QueryOutcome<ANSWER>>.wasAnswered(): Assert<ANSWER> = transform("query answer") { actual ->
    when (actual) {
        is QueryOutcome.Answered -> actual.answer
        is QueryOutcome.Failed -> expected("to be Answered but Failed with reason: ${show(actual.reason)}")
    }
}

fun <ANSWER> Assert<QueryOutcome<ANSWER>>.hasFailed() = given { actual ->
    if (actual !is QueryOutcome.Failed) expected("to have Failed but was ${show(actual)}")
}
