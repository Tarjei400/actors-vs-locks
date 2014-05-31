package com.cerner.devcon.typed;

import scala.concurrent.Future;



public interface BankAccount {

	public Future<Boolean> deposit(double amount);

	public Future<Boolean> withdraw(double amount);

	public Future<Double> balance();

}
