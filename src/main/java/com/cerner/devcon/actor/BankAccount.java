package com.cerner.devcon.actor;

import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;

/**
 * BankAccount actor
 * 
 */
public class BankAccount extends UntypedActor {

	LoggingAdapter log = Logging.getLogger(getContext().system(), this);

	public BankAccount(int accountNumber, double balance) {
		this.accountNumber = accountNumber;
		this.accountBalance = balance;
	}

	private int accountNumber;

	private double accountBalance;

	// to withdraw funds from the account
	private boolean withdraw(double amount) {
		double newAccountBalance;

		if (amount > accountBalance) {
			// there are not enough funds in the account
			return false;
		}

		else {
			newAccountBalance = accountBalance - amount;
			accountBalance = newAccountBalance;

			return true;
		}

	}

	private boolean deposit(double amount) {
		double newAccountBalance;

		if (amount < 0.0) {
			return false; // can not deposit a negative amount
		}

		else {
			newAccountBalance = accountBalance + amount;
			accountBalance = newAccountBalance;
			return true;
		}

	}

	/**
	 * Defines the behavior of the actor to be executed for each message.
	 * Called asynchronously once per message delivered to the actors
	 * mailbox.
	 */
	@Override
	public void onReceive(Object msg) throws Exception {
		// Check the message type to select behavior
		if (msg instanceof Withdraw) {
			withdraw(((Withdraw) msg).getAmount());
			log.debug("sending bank withdraw done");
			// respond async with successful response
			sender().tell(TransactionStatus.DONE, getSelf());
		} else if (msg instanceof Deposit) {
			deposit(((Deposit) msg).getAmount());
			log.debug("sending bank deposit done");
			// respond async with successful response
			sender().tell(TransactionStatus.DONE, getSelf());
		} else if (msg instanceof BalanceRequest) {
			log.debug("sending balance");
			// respond async with successful response
			sender().tell(this.accountBalance, getSelf());
		}

	}

	public static class BalanceRequest {

	}

	public static class Withdraw {
		private double amount;

		public Withdraw(double amount) {
			this.amount = amount;
		}

		public double getAmount() {
			return amount;
		}
	}

	public static class Deposit {
		private double amount;

		public Deposit(double amount) {
			this.amount = amount;
		}

		public double getAmount() {
			return amount;
		}
	}

	public static enum TransactionStatus {
		DONE, FAILED;
	}

	public static Props props(final int accountNumber, final double balance) {
		return Props.create(new BankAccountCreator(accountNumber, balance));
	}

	public static class BankAccountCreator implements Creator<BankAccount> {
		private final long serialVersionUID = 1L;
		private int accountNumber;
		private double balance;

		public BankAccountCreator(final int accountNumber, final double balance) {
			this.accountNumber = accountNumber;
			this.balance = balance;
		}

		@Override
		public BankAccount create() throws Exception {
			return new BankAccount(accountNumber, balance);
		}
	}

}