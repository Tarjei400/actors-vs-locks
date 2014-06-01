package com.cerner.devcon.bank;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * A set of tests to demonstrate race conditions and deadlocks in the naive
 * BankAccount implementation
 * 
 */
public class BankAccountTest {

	private static int taskCount = 100000;
	private static int threadCount = 1;

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
		List<Boolean> results = executeTasks(task);

		for (Boolean result : results) {
			assertTrue(result);
		}
		// Test that the account balance is now the total of all the deposits
		// A smaller number than expected means some threads didn't see the
		// right starting balance.
		assertEquals(results.size() * depositAmt,
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
		List<Boolean> results = executeTasks(task);

		for (Boolean result : results) {
			assertTrue(result);
		}
		// Validate that the ending balance is equal to the starting balance
		// minus or plus the amount transferred
		assertEquals(startingBalance - (results.size() * transferAmt),
				from.accountBalance, .5);
		assertEquals(results.size() * transferAmt, to.accountBalance, .5);

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

		List<Boolean> results = executeAllTasks(tasks);

		for (Boolean result : results) {
			assertTrue(result);
		}
		// Validate that the ending balance of both accounts is the same as the
		// starting balance
		assertEquals(startingBalance, from.accountBalance, .5);
		assertEquals(startingBalance, to.accountBalance, .5);

	}

	private <T> List<T> executeTasks(
			Callable<T> task) throws InterruptedException, ExecutionException {
		List<Callable<T>> tasks = Collections.nCopies(threadCount, task);
		List<T> results = executeAllTasks(tasks);

		return results;
	}

	private <T> List<T> executeAllTasks(
			List<Callable<T>> tasks) throws InterruptedException,
			ExecutionException {
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
