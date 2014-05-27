package com.cerner.devcon.typed;

public interface BankAccountTransfer {

	public TransferStatus transfer(BankAccount from, double amount,
			BankAccount to) ;

	public static enum TransferStatus {
		DONE, FAILED;
	}
}