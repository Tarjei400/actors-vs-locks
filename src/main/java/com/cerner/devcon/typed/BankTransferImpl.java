package com.cerner.devcon.typed;

import com.cerner.devcon.typed.BankAccount.TransactionStatus;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.TypedActor;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Creator;
import akka.japi.Procedure;

public class BankTransferImpl implements BankAccountTransfer {
	
	 LoggingAdapter log = Logging.getLogger(TypedActor.context().system(), TypedActor.context().self());

	@Override
	public TransferStatus transfer(BankAccount from, double amount,
			BankAccount to) {
		if (from.withdraw(amount).equals(TransactionStatus.DONE)) {
			to.deposit(amount);
			return TransferStatus.DONE;
		}
		return TransferStatus.FAILED;

	}
	

}
