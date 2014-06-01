package com.cerner.devcon.typed;

import scala.concurrent.Future;

/**
 * BankAccount interface implemented by TypedActor and the dynamic proxy that is
 * used by calling clients
 * 
 * API returns Futures so that all calls will be nonblocking.  Futures are completed 
 * in TypedActor impl
 */
public interface BankAccount {

	public Future<Boolean> deposit(double amount);

	public Future<Boolean> withdraw(double amount);

	public Future<Double> balance();

}
