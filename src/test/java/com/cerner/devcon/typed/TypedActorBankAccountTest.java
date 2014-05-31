package com.cerner.devcon.typed;

import static akka.dispatch.Futures.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.concurrent.Await;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import akka.actor.TypedProps;
import akka.util.Timeout;

/**
 * Typed Actor test to demonstrate safely handling concurrent state modification
 * 
 */
public class TypedActorBankAccountTest {

	private static final Logger log = LoggerFactory.getLogger(TypedActorBankAccountTest.class);
	
	final FiniteDuration d = Duration.create(10, TimeUnit.SECONDS);
	final Timeout t = Timeout.durationToTimeout(d);
	private static int taskCount = 100000;
	static ActorSystem system;

	/**
	 * Create the ActorSystem once per test. ActorSystems contain global
	 * configuration and resources (ie, thread pools).
	 *
	 * 
	 * @throws Exception
	 */
	@BeforeClass
	public static void init() throws Exception {
		system = ActorSystem.create();
	}

	@AfterClass
	public static void destroy() throws Exception {
		system.shutdown();
	}

	@Test
	public void testSimultaneousDeposit() throws Exception {
		log.info("started deposits");
		final BankAccount account = TypedActor.get(system).typedActorOf(
				BankAccountTypedActor.props(1, 0));
		final double depositAmt = 100;
		List<Future<Boolean>> futures = new ArrayList<Future<Boolean>>();
		for (int i = 0; i < taskCount; i++) {
			futures.add(account.deposit(depositAmt));
		}

		awaitAll(futures);
		assertEquals(futures.size() * depositAmt,
				Await.result(account.balance(), d), 1);

		log.info("finished deposits");
	}

	@Test
	public void testSimultaneousTransferActor() throws Exception {

		log.info("started transfers");
		final double startingBalance = 10 * taskCount;
		final BankAccount from = TypedActor.get(system).typedActorOf(
				BankAccountTypedActor.props(1, 0));
		from.deposit(startingBalance);
		final BankAccount to = TypedActor.get(system).typedActorOf(
				BankAccountTypedActor.props(2, 0));
		to.deposit(startingBalance);
		final double transferAmt = 1;

		List<Future<Boolean>> futures = new ArrayList<Future<Boolean>>();
		for (int i = 0; i < taskCount / 2; i++) {

			final BankAccountTransfer txfr = TypedActor.get(system)
					.typedActorOf(
							new TypedProps<BankTransferTypedActor>(
									BankAccountTransfer.class,
									BankTransferTypedActor.class));

			final BankAccountTransfer txfr2 = TypedActor.get(system)
					.typedActorOf(
							new TypedProps<BankTransferTypedActor>(
									BankAccountTransfer.class,
									BankTransferTypedActor.class));
			futures.add(txfr.transfer(from, transferAmt, to));
			futures.add(txfr2.transfer(to, transferAmt, from));
		}

		awaitAll(futures);
		assertEquals(startingBalance, Await.result(from.balance(), d), .5);
		assertEquals(startingBalance, Await.result(to.balance(), d), .5);

		log.info("finished transfers");

	}
	
	private <T> void awaitAll(List<Future<T>> futures) throws Exception {
		final ExecutionContext ec = system.dispatcher();
		Await.result(sequence(futures, ec), d);

	}

}
