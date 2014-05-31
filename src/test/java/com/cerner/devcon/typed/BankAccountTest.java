package com.cerner.devcon.typed;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import akka.actor.TypedProps;

import com.cerner.devcon.typed.BankAccount.TransactionStatus;
import com.cerner.devcon.typed.BankAccountTransfer.TransferStatus;

public class BankAccountTest {

	private static int taskCount = 100000;
	private static int threadCount = 1;

	private static ExecutorService executorService;
	static ActorSystem system;

	@BeforeClass
	public static void init() throws Exception {
		system = ActorSystem.create();
		executorService = Executors.newFixedThreadPool(threadCount);
	}
	
	@AfterClass
	public static void destroy() throws Exception {
		executorService.shutdown();
		system.shutdown();
	}

	@Test
	public void testSimultaneousDeposit() throws Exception {

		final BankAccount account = TypedActor.get(system).typedActorOf(BankAccountImpl.props(1, 0));
		final double depositAmt = 100;

		Callable<TransactionStatus> task = new Callable<TransactionStatus>() {
			@Override
			public TransactionStatus call() {
				return account.deposit(depositAmt);
			}
		};
		List<Callable<TransactionStatus>> tasks = Collections.nCopies(taskCount, task);

		executeAll(tasks);
		assertEquals(tasks.size() * depositAmt, account.balance(),
				1);

	}

	@Test
	public void testSimultaneousTransfer() throws Exception {

		final double startingBalance = 10 * taskCount;
		final BankAccount from = TypedActor.get(system).typedActorOf(BankAccountImpl.props(1, 0));
		from.deposit(startingBalance);
		final BankAccount to = TypedActor.get(system).typedActorOf(BankAccountImpl.props(2, 0));
		final double transferAmt = 1;

		Callable<Boolean> task = new Callable<Boolean>() {
			@Override
			public Boolean call() {
				return from.withdraw(transferAmt).equals(TransactionStatus.DONE) && to.deposit(transferAmt).equals(TransactionStatus.DONE);
			}
		};
		List<Callable<Boolean>> tasks = Collections.nCopies(taskCount, task);

		executeAll(tasks);
		assertEquals(startingBalance - (tasks.size() * transferAmt),
				from.balance(), .5);
		assertEquals(tasks.size() * transferAmt, to.balance(), .5);

	}

	@Test
	public void testSimultaneousTransferActor() throws Exception {

		final double startingBalance = 10 * taskCount;
		final BankAccount from = TypedActor.get(system).typedActorOf(BankAccountImpl.props(1, 0));
		from.deposit(startingBalance);
		final BankAccount to = TypedActor.get(system).typedActorOf(BankAccountImpl.props(2, 0));
		to.deposit(startingBalance);
		final double transferAmt = 1;
		
		final BankAccountTransfer txfr = TypedActor.get(system).typedActorOf(
			    new TypedProps<BankTransferImpl>(BankAccountTransfer.class, BankTransferImpl.class));

		Callable<TransferStatus> task = new Callable<TransferStatus>() {
			@Override
			public TransferStatus call() {

				return txfr.transfer(from, transferAmt, to);

			}
		};
		List<Callable<TransferStatus>> txfrFrom = Collections.nCopies(taskCount, task);
		

		final BankAccountTransfer txfr2 = TypedActor.get(system).typedActorOf(
			    new TypedProps<BankTransferImpl>(BankAccountTransfer.class, BankTransferImpl.class));

		Callable<TransferStatus> task2 = new Callable<TransferStatus>() {
			@Override
			public TransferStatus call() {

				return txfr2.transfer(to, transferAmt, from);
			}
		};
		List<Callable<TransferStatus>> txfrTo = Collections.nCopies(taskCount, task2);

		List<Callable<TransferStatus>> tasks = new ArrayList<Callable<TransferStatus>>();

		for (int i = 0; i < taskCount; i++) {
			tasks.add(txfrFrom.get(i));
			tasks.add(txfrTo.get(i));
		}

		executeAll(tasks);
		assertEquals(startingBalance, from.balance(), .5);
		assertEquals(startingBalance, to.balance(), .5);

	}

	private <T> void executeAll(List<Callable<T>> tasks)
			throws InterruptedException, ExecutionException {
		List<Future<T>> futures = executorService.invokeAll(tasks); // /DEADLOCK!!!
		List<T> resultList = new ArrayList<T>(futures.size());
		// Check for exceptions
		for (Future<T> future : futures) {
			// Throws an exception if an exception was thrown by the task.
			resultList.add(future.get());
		}
		// Validate the number of exec tasks
		assertEquals(tasks.size(), futures.size());
	}

}
