package com.cerner.devcon.typed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.cerner.devcon.typed.BankAccount.TransactionStatus;
import com.cerner.devcon.typed.BankAccountImpl;
import com.cerner.devcon.typed.BankAccountTransfer.TransferStatus;

import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import akka.actor.TypedProps;

public class BankAccountTest {

	private static int taskCount = 100000;
	private static int threadCount = 100;

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
	}

//	@Test
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
		List<Future<TransactionStatus>> futures = executorService.invokeAll(tasks);
		List<TransactionStatus> resultList = new ArrayList<TransactionStatus>(futures.size());
		// Check for exceptions
		for (Future<TransactionStatus> future : futures) {
			// Throws an exception if an exception was thrown by the task.
			resultList.add(future.get());
		}
		// Validate the number of exec tasks
		Assert.assertEquals(taskCount, futures.size());
		Assert.assertEquals(tasks.size() * depositAmt, account.balance(),
				1);

	}

//	@Test
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
		List<Future<Boolean>> futures = executorService.invokeAll(tasks);
		List<Boolean> resultList = new ArrayList<Boolean>(futures.size());
		// Check for exceptions
		for (Future<Boolean> future : futures) {
			// Throws an exception if an exception was thrown by the task.
			resultList.add(future.get());
		}
		// Validate the number of exec tasks
		Assert.assertEquals(taskCount, futures.size());
		Assert.assertEquals(startingBalance - (tasks.size() * transferAmt),
				from.balance(), .5);
		Assert.assertEquals(tasks.size() * transferAmt, to.balance(), .5);

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

		List<Future<TransferStatus>> futures = executorService.invokeAll(tasks); // /DEADLOCK!!!
		List<TransferStatus> resultList = new ArrayList<TransferStatus>(futures.size());
		// Check for exceptions
		for (Future<TransferStatus> future : futures) {
			// Throws an exception if an exception was thrown by the task.
			resultList.add(future.get());
		}
		// Validate the number of exec tasks
		Assert.assertEquals(taskCount * 2, futures.size());
		Assert.assertEquals(startingBalance, from.balance(), .5);
		Assert.assertEquals(startingBalance, to.balance(), .5);

	}

}
