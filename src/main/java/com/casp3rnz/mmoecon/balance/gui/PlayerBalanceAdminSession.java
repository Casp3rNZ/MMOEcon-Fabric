package com.casp3rnz.mmoecon.balance.gui;

import com.casp3rnz.mmoecon.balance.BalanceAdministration;
import com.casp3rnz.mmoecon.balance.BalanceTargets;

import java.util.List;
import java.util.UUID;

/** Per-menu state. Account clicks use the displayed snapshot, never a re-sorted list. */
public class PlayerBalanceAdminSession {
    public BalanceAdminViews view = BalanceAdminViews.ACCOUNTS;
    public List<BalanceTargets.Target> accounts = List.of();
    public int page = 0;
    public UUID target = null;
    public BalanceAdministration.Operation operation = BalanceAdministration.Operation.SET;
    public long amount = 0L;
    public long confirmationBalance = 0L;
}
