package sollecitom.lattice.test.utils

import assertk.Assert
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import sollecitom.lattice.core.Answered
import sollecitom.lattice.core.Command
import sollecitom.lattice.core.CommandReceived
import sollecitom.lattice.core.Position
import sollecitom.lattice.core.Recorded
import sollecitom.lattice.core.Positioned

fun Assert<Positioned>.wasRecordedAfter(other: Positioned) = given { actual ->

    assertThat(actual.position, name = "position").isGreaterThan(other.position)
}

fun Assert<Answered<*>>.sawAtLeast(other: Positioned) = given { actual ->

    assertThat(actual.appliedThrough, name = "appliedThrough").isGreaterThanOrEqualTo(other.position)
}

fun Command.recordedAsReceivedAt(position: Position) = Recorded(CommandReceived(id, this), position)

fun Assert<Recorded<*>>.recordsReceptionOf(command: Command, at: Position) = given { actual ->

    assertThat(actual).isEqualTo(command.recordedAsReceivedAt(at))
}
