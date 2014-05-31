package com.cerner.devcon.typed;

import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import akka.actor.TypedActor;
import akka.dispatch.Futures;
import akka.dispatch.Mapper;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class BankTransferTypedActor implements BankAccountTransfer {

	LoggingAdapter log = Logging.getLogger(TypedActor.context().system(),
			TypedActor.context().self());

	@Override
	public Future<Boolean> transfer(final BankAccount from,
			final double amount, final BankAccount to) {
		final ExecutionContext ec = TypedActor.dispatcher();
		Future<Boolean> f = from.withdraw(amount).flatMap(
				new Mapper<Boolean, Future<Boolean>>() {
					public Future<Boolean> apply(Boolean result) {
						if (result) {
							log.debug("txfr done");
							return to.deposit(amount);
						} else {
							return Futures.successful(false);
						}
					}
				}, ec);

		return f;

	}

}
