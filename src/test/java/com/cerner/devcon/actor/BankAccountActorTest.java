package com.cerner.devcon.actor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import com.cerner.devcon.actor.BankAccount;
import com.cerner.devcon.actor.BankTransfer;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.testkit.JavaTestKit;
import akka.util.Timeout;
import static akka.pattern.Patterns.*;

public class BankAccountActorTest {

	final FiniteDuration d = Duration.create(5, TimeUnit.SECONDS);
	final Timeout t = Timeout.durationToTimeout(d);

	public static class BankTeller extends UntypedActor {

		LoggingAdapter log = Logging.getLogger(getContext().system(), this);

		private ActorRef accountA = getContext().actorOf(
				BankAccount.props(1, 0));
		private ActorRef accountB = getContext().actorOf(
				BankAccount.props(2, 0));

		private ActorRef probe;

		public void onReceive(Object msg) {

			if (msg.equals("start")) {
				accountA.tell(new BankAccount.Deposit(1000), getSelf());
				probe.tell("started", getSelf());

			} else if (msg.equals(BankAccount.TransactionStatus.DONE)) {
				log.debug("deposit done");
				probe.tell("deposited", getSelf());
				ActorRef txfr = getContext().actorOf(
						Props.create(BankTransfer.class), "aToBtxfr");
				txfr.tell(new BankTransfer.Transfer(accountA, accountB, 150),
						getSelf());
			} else if (msg.equals(BankTransfer.TransferStatus.DONE)) {
				log.debug("txfr done");
				probe.tell("done", getSelf());
			} else if (msg.equals(BankTransfer.TransferStatus.FAILED)) {
				log.debug("txfr failed");
			} else if (msg instanceof ActorRef) {
				probe = (ActorRef) msg;
			} else {
				log.debug("unknown msg");
			}
		}

	}

	static ActorSystem system;

	@BeforeClass
	public static void setup() {
		system = ActorSystem.create();
	}

	@AfterClass
	public static void teardown() {
		JavaTestKit.shutdownActorSystem(system);
		system = null;
	}

	@Test
	public void testSimultaneousDeposit() throws Exception {

		final int threadCount = 100;

		new JavaTestKit(system) {
			{

				final ActorRef accountA = system.actorOf(BankAccount.props(1, 0));

				// the run() method needs to finish within 3 seconds
				new Within(duration("30 seconds")) {
					protected void run() {

						final ActorRef testRef = getRef();

						final double depositAmt = 100;

						Callable<Boolean> task = new Callable<Boolean>() {
							@Override
							public Boolean call() {
								scala.concurrent.Future<Object> f = ask(
										accountA,
										new BankAccount.Deposit(depositAmt), t);
								try {
									Await.result(f, d);
									return true;
								} catch (Exception e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
									return false;
								}
							}
						};

						List<Callable<Boolean>> tasks = Collections.nCopies(
								threadCount, task);
						ExecutorService executorService = Executors
								.newFixedThreadPool(threadCount);
						List<Future<Boolean>> futures;
						try {
							futures = executorService.invokeAll(tasks);

							List<Boolean> resultList = new ArrayList<Boolean>(
									futures.size());
							// Check for exceptions
							for (Future<Boolean> future : futures) {
								// Throws an exception if an exception was
								// thrown by the task.
								resultList.add(future.get());
							}
							scala.concurrent.Future<Object> answer = ask(
									accountA, new BankAccount.BalanceRequest(),
									t);
							double balance = (Double) Await.result(answer, d);
							// Validate the number of exec tasks
							Assert.assertEquals(threadCount, futures.size());
							Assert.assertEquals(tasks.size() * depositAmt,
									balance, 1);
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

					}
				};
			}
		};
	}

	@Test
	public void testTransfer() {
		/*
		 * Wrap the whole test procedure within a testkit constructor if you
		 * want to receive actor replies or use Within(), etc.
		 */
		new JavaTestKit(system) {
			{
				final Props props = Props.create(BankTeller.class);
				final ActorRef teller = system.actorOf(props);

				// can also use JavaTestKit “from the outside”
				final JavaTestKit probe = new JavaTestKit(system);
				// “inject” the probe by passing it to the test subject
				// like a real resource would be passed in production
				teller.tell(probe.getRef(), getRef());

				// the run() method needs to finish within 3 seconds
				new Within(duration("2 seconds")) {
					protected void run() {

						teller.tell("start", getRef());
						probe.expectMsgEquals(duration("1 seconds"), "started");

						probe.expectMsgEquals(duration("1 seconds"),
								"deposited");

						probe.expectMsgEquals(duration("2 seconds"), "done");

						// Will wait for the rest of the 3 seconds
						expectNoMsg();
					}
				};
			}
		};
	}

}
