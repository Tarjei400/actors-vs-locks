package com.cerner.devcon.bank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Assert;
import org.junit.Test;



public class BankAccountTest {
	
	private int threadCount = 1;

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
	
	
	@Test
	public void testSimultaneousTransfer() throws Exception {
		
		final double startingBalance = 1000;
		final BankAccount from = new BankAccount();
		from.deposit(startingBalance);
		final BankAccount to = new BankAccount();
		final double transferAmt = 1;
		
	    Callable<Boolean> task = new Callable<Boolean>() {
	        @Override
	        public Boolean call() {
	            return from.withdraw(transferAmt) && to.deposit(transferAmt);
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
	    Assert.assertEquals(startingBalance - (tasks.size() * transferAmt), from.accountBalance, .5);
	    Assert.assertEquals(tasks.size() * transferAmt, to.accountBalance, .5);
		
	}
	
	@Test
	public void testSimultaneousTransferSynch() throws Exception {
		

	    ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
	    
		final double startingBalance = 1000;
		final BankAccount from = new BankAccount();
		from.deposit(startingBalance);
		final BankAccount to = new BankAccount();
		to.deposit(startingBalance);
		final double transferAmt = 1;
		
	    Callable<Boolean> task = new Callable<Boolean>() {
	        @Override
	        public Boolean call() {
	        	
	        	return BankAccountTransfer.transfer(from, transferAmt, to);
				
	        }
	    };
	    List<Callable<Boolean>> txfrFrom = Collections.nCopies(threadCount , task);
	    
		
	    Callable<Boolean> task2 = new Callable<Boolean>() {
	        @Override
	        public Boolean call() {
	        
	        	return BankAccountTransfer.transfer(to, transferAmt, from);
	        }
	    };
	    List<Callable<Boolean>> txfrTo = Collections.nCopies(threadCount , task2);
	    
	    List<Callable<Boolean>> tasks = new ArrayList<Callable<Boolean>>();
	    
	    
	    for (int i =0 ; i < threadCount; i++) {
	    	tasks.add(txfrFrom.get(i));
	    	tasks.add(txfrTo.get(i));
	    }
	    
	    List<Future<Boolean>> futures = executorService.invokeAll(tasks);  ///DEADLOCK!!!
	    List<Boolean> resultList = new ArrayList<Boolean>(futures.size());
	    // Check for exceptions
	    for (Future<Boolean> future : futures) {
	        // Throws an exception if an exception was thrown by the task.
	        resultList.add(future.get());
	    }
	    // Validate the number of exec tasks
	    Assert.assertEquals(threadCount * 2, futures.size());
	    Assert.assertEquals(startingBalance, from.accountBalance, .5);
	    Assert.assertEquals(startingBalance, to.accountBalance, .5);
		
	}
	
	
	

}
