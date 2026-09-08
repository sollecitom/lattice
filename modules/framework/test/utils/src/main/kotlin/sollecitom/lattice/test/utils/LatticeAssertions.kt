package sollecitom.lattice.test.utils

import assertk.Assert
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import sollecitom.lattice.core.Answered
import sollecitom.lattice.core.Positioned

fun Assert<Positioned>.wasRecordedAfter(other: Positioned) = given { actual ->

    assertThat(actual.position, name = "position").isGreaterThan(other.position)
}

fun Assert<Answered<*>>.sawAtLeast(other: Positioned) = given { actual ->

    assertThat(actual.appliedThrough, name = "appliedThrough").isGreaterThanOrEqualTo(other.position)
}
