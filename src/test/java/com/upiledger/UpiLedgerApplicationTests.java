package com.upiledger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpiLedgerApplicationTests {

    @Test
    void applicationClassHasExpectedPackage() {
        assertEquals("com.upiledger", UpiLedgerApplication.class.getPackageName());
    }
}
