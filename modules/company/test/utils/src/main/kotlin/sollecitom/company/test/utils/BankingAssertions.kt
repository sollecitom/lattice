package sollecitom.company.test.utils

import assertk.Assert
import assertk.assertThat
import assertk.assertions.isEqualTo
import sollecitom.company.domain.Deposit
import sollecitom.company.domain.DepositProcessed
import sollecitom.lattice.core.Recorded

fun Assert<Recorded<DepositProcessed>>.isResultOf(command: Deposit, leavingBalance: Long) = given { actual ->

    assertThat(actual.value).isEqualTo(DepositProcessed(command.accountId, command.amount, leavingBalance))
}
