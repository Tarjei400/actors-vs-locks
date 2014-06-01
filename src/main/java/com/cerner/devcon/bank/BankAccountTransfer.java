package com.cerner.devcon.bank;

public class BankAccountTransfer {

	/**
	 * Transfer by locking both accounts and then withdrawing and depositing
	 * funds.
	 * 
	 * Deadlock occurs when two transfers execute at the same time in opposite
	 * directions using the same accounts.
	 * 
	 * @param from
	 * @param amount
	 * @param to
	 * @return
	 */
	public static boolean transfer(BankAccount from, double amount,
			BankAccount to) {
		synchronized (from) {
			synchronized (to) {
				if (from.withdraw(amount)) {
					to.deposit(amount);
					return true;
				}
			}

		}
		return false;

	}

}