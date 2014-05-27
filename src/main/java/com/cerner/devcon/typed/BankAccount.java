package com.cerner.devcon.typed;



public interface BankAccount {

	public abstract TransactionStatus deposit(double amount);

	public abstract TransactionStatus withdraw(double amount);

	double balance();
	
	public static enum TransactionStatus {
		DONE, FAILED;
	}


}
