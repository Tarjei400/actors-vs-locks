package com.cerner.devcon.bank;

public class BankAccountTransfer {

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