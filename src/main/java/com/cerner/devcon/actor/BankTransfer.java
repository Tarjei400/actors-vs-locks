package com.cerner.devcon.actor;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.Procedure;

public class BankTransfer extends UntypedActor {
	
	 LoggingAdapter log = Logging.getLogger(getContext().system(), this);

	@Override
	public void onReceive(Object msg) throws Exception {
		if (msg instanceof Transfer) {
			log.debug("received transfer message");
			Transfer txfr = (Transfer) msg;
			txfr.from.tell(new BankAccount.Withdraw(txfr.getAmount()),
					getSelf());
			getContext().become(
					new AwaitFrom(txfr.to, txfr.amount, getSender()));
		}

	}

	private class AwaitFrom implements Procedure<Object> {

		private ActorRef to;
		private double amount;
		private ActorRef customer;

		public AwaitFrom(final ActorRef to, final double amount,
				final ActorRef customer) {
			this.to = to;
			this.amount = amount;
			this.customer = customer;
		}

		@Override
		public void apply(Object msg) {
			if (msg instanceof BankAccount.TransactionStatus) {
				BankAccount.TransactionStatus status = (BankAccount.TransactionStatus) msg;
				switch (status) {
				case DONE:
					log.debug("received transfer withdraw done");
					to.tell(new BankAccount.Deposit(amount), getSelf());
					getContext().become(new AwaitTo(customer));
					break;
				case FAILED:
					log.debug("received transfer withdraw failed");
					customer.tell(TransferStatus.FAILED, getSelf());
					getContext().stop(getSelf());
					break;
				}
			}
		}

	};

	private class AwaitTo implements Procedure<Object> {

		private ActorRef customer;

		public AwaitTo(final ActorRef customer) {
			this.customer = customer;
		}

		@Override
		public void apply(Object msg) {
			if (msg instanceof BankAccount.TransactionStatus) {
				BankAccount.TransactionStatus status = (BankAccount.TransactionStatus) msg;
				switch (status) {
				case DONE:
					log.debug("received transfer deposit done");
					customer.tell(TransferStatus.DONE, getSelf());
					getContext().stop(getSelf());
					break;
				case FAILED:
					log.debug("received transfer deposit failed");
					customer.tell(TransferStatus.FAILED, getSelf());
					getContext().stop(getSelf());
					break;
				}
			}
		}

	};


	public static class Transfer {
		private double amount;
		private ActorRef from;
		private ActorRef to;

		public Transfer(ActorRef from, ActorRef to, double amount) {
			this.amount = amount;
			this.from = from;
			this.to = to;
		}

		public double getAmount() {
			return amount;
		}

		public ActorRef getFrom() {
			return from;
		}

		public ActorRef getTo() {
			return to;
		}

	}

	public static enum TransferStatus {
		DONE, FAILED;
	}
}
