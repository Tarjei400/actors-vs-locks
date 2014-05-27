package com.cerner.devcon.typed;


import akka.actor.TypedActor;
import akka.actor.TypedProps;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;

public class BankAccountImpl implements BankAccount {

	
	LoggingAdapter log = Logging.getLogger(TypedActor.context().system(), TypedActor.context().self());
	 
	public BankAccountImpl(int accountNumber, double balance) {
		this.accountNumber = accountNumber;
		this.accountBalance = balance;
	}

	int accountNumber;

	double accountBalance;

	// to withdraw funds from the account
	@Override
	public TransactionStatus withdraw(double amount) {
		
		
		double newAccountBalance;

		if (amount > accountBalance) {
			// there are not enough funds in the account
			return TransactionStatus.FAILED;
		}

		else {
			newAccountBalance = accountBalance - amount;
			accountBalance = newAccountBalance;

			log.debug("bank withdraw done");
			return TransactionStatus.DONE;
		}

	}

	@Override
	public TransactionStatus deposit(double amount) {
		double newAccountBalance;

		if (amount < 0.0) {
			return TransactionStatus.FAILED; // can not deposit a negative amount
		}

		else {
			newAccountBalance = accountBalance + amount;
			accountBalance = newAccountBalance;
			log.debug("sending bank deposit done");
			return TransactionStatus.DONE;
		}

	}

	@Override
	public double balance() {
		log.debug("sending balance");
		return this.accountBalance;
	}
	
	
	public static TypedProps<BankAccountImpl> props(final int accountNumber, final double balance) {
		return new TypedProps<BankAccountImpl>(BankAccount.class, new BankAccountCreator(accountNumber, balance));
	}
	
	public static class BankAccountCreator implements Creator<BankAccountImpl> {
		private final long serialVersionUID = 1L;
		private int accountNumber;
		private double balance;

		public BankAccountCreator(final int accountNumber, final double balance) {
			this.accountNumber =accountNumber;
			this.balance = balance;
		}
		
		@Override
		public BankAccountImpl create() throws Exception {
			return new BankAccountImpl(accountNumber, balance);
		}
	}

}