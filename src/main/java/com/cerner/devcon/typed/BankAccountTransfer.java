package com.cerner.devcon.typed;

import scala.concurrent.Future;

public interface BankAccountTransfer {

	public Future<Boolean> transfer(BankAccount from, double amount,
			BankAccount to) ;

}