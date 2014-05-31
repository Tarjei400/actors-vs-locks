package com.cerner.devcon.bank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * A set of tests to demonstrate race conditions and deadlocks in the naive
 * BankAccount implementation
 * 
 */
public class BankAccountTest {

	private static int taskCount = 1000;
	private static int threadCount = 2;

	private static ExecutorService executorService;

	@BeforeClass
	public static void init() throws Exception {
		executorService = Executors.newFixedThreadPool(threadCount);
	}

	@AfterClass
	public static void destroy() throws Exception {
		executorService.shutdown();
	}

	/**
	 * Creates a bank account and simultaneously executes deposits from multiple
	 * threads to trigger a race condition
	 * 
	 * @throws Exception
	 */
	@Test
	public void testSimultaneousDeposit() throws Exception {
		// Create a bank account
		final BankAccount account = new BankAccount();
		final double depositAmt = 100;

		Callable<Boolean> task = new Callable<Boolean>() {
			@Override
			public Boolean call() {
				return account.deposit(depositAmt);
			}
		};
		// Execute all the deposits on the thread pool
		List<Future<Boolean>> futures = executeTasks(task);

		// Test that the account balance is now the total of all the deposits
		// A smaller number than expected means some threads didn't see the
		// right starting balance.
		Assert.assertEquals(futures.size() * depositAmt,
				account.accountBalance, 1);

	}

	/**
	 * Tests simultaneous transfers without locks to demonstrate a race
	 * condition
	 * 
	 * @throws Exception
	 */
	@Test
	public void testSimultaneousTransfer() throws Exception {
		// Start with a big enough balance to cover the txfrs
		final double startingBalance = 10 * taskCount;
		final BankAccount from = new BankAccount();
		from.deposit(startingBalance);
		final BankAccount to = new BankAccount();
		final double transferAmt = 1;

		// Create a task to transfer once
		Callable<Boolean> task = new Callable<Boolean>() {
			@Override
			public Boolean call() {
				return from.withdraw(transferAmt) && to.deposit(transferAmt);
			}
		};

		// Execute all transfers on the thread pool
		List<Future<Boolean>> futures = executeTasks(task);

		// Validate that the ending balance is equal to the starting balance
		// minus or plus the amount transferred
		Assert.assertEquals(startingBalance - (futures.size() * transferAmt),
				from.accountBalance, .5);
		Assert.assertEquals(futures.size() * transferAmt, to.accountBalance, .5);

	}

	/**
	 * Test simultaneous transfers with locks on the accounts involved in the
	 * transfer. Results in a deadlock due to lock ordering.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testSimultaneousTransferSynch() throws Exception {

		// Start with a big enough balance to cover the txfrs
		final double startingBalance = 10 * taskCount;
		final BankAccount from = new BankAccount();
		from.deposit(startingBalance);
		final BankAccount to = new BankAccount();
		to.deposit(startingBalance);
		final double transferAmt = 1;

		// Create a task to transfer an amount by locking the two accounts
		Callable<Boolean> task = new Callable<Boolean>() {
			@Override
			public Boolean call() {

				return BankAccountTransfer.transfer(from, transferAmt, to);

			}
		};
		List<Callable<Boolean>> txfrFrom = Collections.nCopies(taskCount, task);

		// Create a task to transfer the same amount in the opposite direction
		Callable<Boolean> task2 = new Callable<Boolean>() {
			@Override
			public Boolean call() {

				return BankAccountTransfer.transfer(to, transferAmt, from);
			}
		};
		List<Callable<Boolean>> txfrTo = Collections.nCopies(taskCount, task2);

		List<Callable<Boolean>> tasks = new ArrayList<Callable<Boolean>>();

		for (int i = 0; i < taskCount; i++) {
			tasks.add(txfrFrom.get(i));
			tasks.add(txfrTo.get(i));
		}

		List<Future<Boolean>> futures = executeAllTasks(tasks);

		// Validate that the ending balance of both accounts is the same as the
		// starting balance
		Assert.assertEquals(startingBalance, from.accountBalance, .5);
		Assert.assertEquals(startingBalance, to.accountBalance, .5);

	}

	private List<Future<Boolean>> executeTasks(Callable<Boolean> task)
			throws InterruptedException, ExecutionException {
		List<Callable<Boolean>> tasks = Collections.nCopies(taskCount, task);
		List<Future<Boolean>> futures = executeAllTasks(tasks);

		return futures;
	}

	private List<Future<Boolean>> executeAllTasks(List<Callable<Boolean>> tasks)
			throws InterruptedException, ExecutionException {
		List<Future<Boolean>> futures = executorService.invokeAll(tasks);
		List<Boolean> resultList = new ArrayList<Boolean>(futures.size());
		// Check for exceptions
		for (Future<Boolean> future : futures) {
			// Throws an exception if an exception was thrown by the task.
			resultList.add(future.get());
		}
		// Validate the number of exec tasks
		Assert.assertEquals(tasks.size(), futures.size());
		return futures;
	}

}
