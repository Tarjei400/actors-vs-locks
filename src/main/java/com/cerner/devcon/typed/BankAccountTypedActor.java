package com.cerner.devcon.typed;


import scala.concurrent.Future;
import akka.actor.TypedActor;
import akka.actor.TypedProps;
import akka.dispatch.Futures;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;

public class BankAccountTypedActor implements BankAccount {

	
	LoggingAdapter log = Logging.getLogger(TypedActor.context().system(), TypedActor.context().self());
	 
	public BankAccountTypedActor(int accountNumber, double balance) {
		this.accountNumber = accountNumber;
		this.accountBalance = balance;
	}

	int accountNumber;

	double accountBalance;

	// to withdraw funds from the account
	@Override
	public Future<Boolean> withdraw(double amount) {
		
		
		double newAccountBalance;

		if (amount > accountBalance) {
			// there are not enough funds in the account
			return Futures.successful(false);
		}

		else {
			newAccountBalance = accountBalance - amount;
			accountBalance = newAccountBalance;

			log.debug("bank withdraw done");
			return Futures.successful(true);
		}

	}

	@Override
	public Future<Boolean> deposit(double amount) {
		double newAccountBalance;

		if (amount < 0.0) {
			return Futures.successful(false); // can not deposit a negative amount
		}

		else {
			newAccountBalance = accountBalance + amount;
			accountBalance = newAccountBalance;
			log.debug("sending bank deposit done");
			return Futures.successful(true);
		}

	}

	@Override
	public Future<Double> balance() {
		log.debug("sending balance");
		return Futures.successful(accountBalance);
	}
	
	
	public static TypedProps<BankAccountTypedActor> props(final int accountNumber, final double balance) {
		return new TypedProps<BankAccountTypedActor>(BankAccount.class, new BankAccountCreator(accountNumber, balance));
	}
	
	public static class BankAccountCreator implements Creator<BankAccountTypedActor> {
		private final long serialVersionUID = 1L;
		private int accountNumber;
		private double balance;

		public BankAccountCreator(final int accountNumber, final double balance) {
			this.accountNumber =accountNumber;
			this.balance = balance;
		}
		
		@Override
		public BankAccountTypedActor create() throws Exception {
			return new BankAccountTypedActor(accountNumber, balance);
		}
	}

}