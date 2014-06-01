package com.cerner.devcon.typed;

import scala.concurrent.Future;

/**
 * BankAccountTransfer interface implemented by TypedActor and the dynamic proxy that is
 * used by calling clients
 * 
 * API returns Futures so that all calls will be nonblocking.  Futures are completed 
 * in TypedActor impl
 */
public interface BankAccountTransfer {

	public Future<Boolean> transfer(BankAccount from, double amount,
			BankAccount to) ;

}