package com.cerner.devcon.bank;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.junit.Assert;
import org.junit.Test;



public class BankAccountTest {
	
	private int threadCount = 100;

	@Test
	public void testSimultaneousDeposit() throws Exception {
		
		final BankAccount account = new BankAccount();
		final double depositAmt = 100;
		
	    Callable<Boolean> task = new Callable<Boolean>() {
	        @Override
	        public Boolean call() {
	            return account.deposit(depositAmt);
	        }
	    };
	    List<Callable<Boolean>> tasks = Collections.nCopies(threadCount , task);
	    ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
	    List<Future<Boolean>> futures = executorService.invokeAll(tasks);
	    List<Boolean> resultList = new ArrayList<Boolean>(futures.size());
	    // Check for exceptions
	    for (Future<Boolean> future : futures) {
	        // Throws an exception if an exception was thrown by the task.
	        resultList.add(future.get());
	    }
	    // Validate the number of exec tasks
	    Assert.assertEquals(threadCount, futures.size());
	    Assert.assertEquals(tasks.size() * depositAmt, account.accountBalance, 1);
		
	}
	
	
	
	private class Deposit implements Runnable {

		private BankAccount target;
		private double amount;

		public Deposit(final BankAccount target, final double amount) {
			this.target = target;
			this.amount = amount;
		}
		
		@Override
		public void run() {
			target.deposit(amount);
		}
		
	}
	
	

}
