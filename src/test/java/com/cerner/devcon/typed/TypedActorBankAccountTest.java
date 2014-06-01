package com.cerner.devcon.typed;

import static akka.dispatch.Futures.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import akka.dispatch.Futures;
import akka.util.Timeout;

/**
 * Typed Actor test to demonstrate safely handling concurrent state
 * modification.
 * 
 */
public class TypedActorBankAccountTest {

	private static final Logger log = LoggerFactory
			.getLogger(TypedActorBankAccountTest.class);

	final FiniteDuration d = Duration.create(10, TimeUnit.SECONDS);
	final Timeout t = Timeout.durationToTimeout(d);
	private static int taskCount = 100000;
	private static int threadCount = 1;
	static ActorSystem system;

	private static ExecutorService executorService;

	/**
	 * Create the ActorSystem once per test. ActorSystems contain global
	 * configuration and resources (ie, thread pools).
	 * 
	 * The executer thread pool is only used for simulating client requests.
	 * 
	 * @throws Exception
	 */
	@BeforeClass
	public static void init() throws Exception {
		system = ActorSystem.create();
		executorService = Executors.newFixedThreadPool(threadCount);
	}

	@AfterClass
	public static void destroy() throws Exception {
		system.shutdown();
		executorService.shutdown();
	}

	/**
	 * Test simultaneous deposits sent to a single actor. Threads simulate
	 * clients and tasks are divided between them.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testSimultaneousDeposit() throws Exception {
		log.info("started deposits");
		// Create a TypedActor
		// A dynamic proxy is returned that wraps the interaction with the
		// ActorRef
		final BankAccount account = TypedActor.get(system).typedActorOf(
				BankAccountTypedActor.props(1, 0));
		final double depositAmt = 100;

		Callable<Boolean> task = new Callable<Boolean>() {
			@Override
			public Boolean call() {
				List<Future<Boolean>> futures = new ArrayList<Future<Boolean>>();
				// Each client sends its workload to the ActorRef in nonblocking
				// calls, so that they are executed in parallel.
				for (int i = 0; i < taskCount / threadCount; i++) {
					futures.add(account.deposit(depositAmt));
				}
				Iterable<Boolean> results = awaitAll(futures);
				for (Boolean result : results) {
					assertTrue(result);
				}
				return true;
			}
		};

		List<Boolean> results = executeTasks(task);

		// Validate the results.
		for (Boolean result : results) {
			assertTrue(result);
		}
		assertEquals(taskCount * depositAmt,
				Await.result(account.balance(), d), 1);

		log.info("finished deposits");
	}

	/**
	 * Test simultaneous transfers between 2 actors. Threads simulate clients
	 * and tasks are divided between them.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testSimultaneousTransferActor() throws Exception {

		log.info("started transfers");
		final double startingBalance = 10 * taskCount;

		// Create 2 TypedActors
		// A dynamic proxy is returned that wraps the interaction with the
		// ActorRef
		final BankAccount from = TypedActor.get(system).typedActorOf(
				BankAccountTypedActor.props(1, 0));
		from.deposit(startingBalance);
		final BankAccount to = TypedActor.get(system).typedActorOf(
				BankAccountTypedActor.props(2, 0));
		to.deposit(startingBalance);
		final double transferAmt = 1;

		Callable<Boolean> task = new Callable<Boolean>() {
			@Override
			public Boolean call() {
				List<Future<Boolean>> futures = new ArrayList<Future<Boolean>>();

				// Each client sends its workload to the ActorRef in nonblocking
				// calls, so that they are executed in parallel.
				for (int i = 0; i < taskCount / threadCount / 2; i++) {

					// Create two transfers, one in each direction
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

				Iterable<Boolean> results = awaitAll(futures);
				for (Boolean result : results) {
					assertTrue(result);
				}
				return true;
			}
		};

		List<Boolean> results = executeTasks(task);

		// Validate the results.
		for (Boolean result : results) {
			assertTrue(result);
		}
		assertEquals(startingBalance, Await.result(from.balance(), d), .5);
		assertEquals(startingBalance, Await.result(to.balance(), d), .5);

		log.info("finished transfers");

	}

	private <T> Iterable<T> awaitAll(List<Future<T>> futures) {
		final ExecutionContext ec = system.dispatcher();
		try {
			return Await.result(sequence(futures, ec), d);
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		return null;

	}

	private <T> List<T> executeTasks(Callable<T> task)
			throws InterruptedException, ExecutionException {
		List<Callable<T>> tasks = Collections.nCopies(threadCount, task);
		List<T> results = executeAllTasks(tasks);

		return results;
	}

	private <T> List<T> executeAllTasks(List<Callable<T>> tasks)
			throws InterruptedException, ExecutionException {
		List<java.util.concurrent.Future<T>> futures = executorService
				.invokeAll(tasks);
		List<T> resultList = new ArrayList<T>(futures.size());
		// Check for exceptions
		for (java.util.concurrent.Future<T> future : futures) {
			// Throws an exception if an exception was thrown by the task.
			resultList.add(future.get());
		}
		// Validate the number of exec tasks
		assertEquals(tasks.size(), resultList.size());
		return resultList;
	}

}
