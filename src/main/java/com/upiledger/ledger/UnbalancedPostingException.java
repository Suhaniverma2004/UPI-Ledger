package com.upiledger.ledger;

import java.math.BigDecimal;

public class UnbalancedPostingException extends RuntimeException {
    public UnbalancedPostingException(BigDecimal debit, BigDecimal credit) {
        super("Ledger posting is unbalanced: debits=" + debit + ", credits=" + credit);
    }
}
