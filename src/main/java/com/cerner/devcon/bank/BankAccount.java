package com.cerner.devcon.bank;

public class BankAccount {

	int accountNumber;

	double accountBalance;

	// to withdraw funds from the account
	public boolean withdraw(double amount) {
		double newAccountBalance;

		if (amount > accountBalance) {
			// there are not enough funds in the account
			return false;
		}

		else {
			// calculate the new balance from the old. Race condition occurs
			// when the next lines are interleaved between 2 or more
			// threads.  Sleeping may be required to slow things down enough.
			newAccountBalance = accountBalance - amount;
//			try {
//				Thread.sleep(100);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
			accountBalance = newAccountBalance;
			return true;
		}

	}

	public boolean deposit(double amount) {
		double newAccountBalance;

		if (amount < 0.0) {
			return false; // can not deposit a negative amount
		}

		else {
			// calculate the new balance from the old. Race condition occurs
			// when the next lines are interleaved between 2 or more
			// threads.  Sleeping may be required to slow things down enough.
			newAccountBalance = accountBalance + amount;
//			try {
//				Thread.sleep(100);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
			accountBalance = newAccountBalance;
			return true;
		}

	}
}