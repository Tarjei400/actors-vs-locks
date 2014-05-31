package com.cerner.devcon.actor;

import static akka.pattern.Patterns.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.testkit.JavaTestKit;
import akka.util.Timeout;

/**
 * Tests the UntypedActors
 *
 */
public class BankAccountActorTest {

	final FiniteDuration d = Duration.create(5, TimeUnit.SECONDS);
	final Timeout t = Timeout.durationToTimeout(d);

	private static final int taskCount = 100000;
	private static final int threadCount = 1;

	static ExecutorService executorService;

	static ActorSystem system;

	@BeforeClass
	public static void setup() {
		system = ActorSystem.create();
		executorService = Executors.newFixedThreadPool(threadCount);
	}

	@AfterClass
	public static void teardown() {
		executorService.shutdown();
		JavaTestKit.shutdownActorSystem(system);
	}

	public static class BankTeller extends UntypedActor {

		LoggingAdapter log = Logging.getLogger(getContext().system(), self());

		private final int tellerTxfrs = taskCount / threadCount;

		private ActorRef accountA = getContext().actorOf(
				BankAccount.props(1, 0));
		private ActorRef accountB = getContext().actorOf(
				BankAccount.props(2, 0));

		private ActorRef probe;

		private List<ActorRef> txfrs = new ArrayList<ActorRef>();
		private int txfrCount = 0;

		public void onReceive(Object msg) {

			if (msg.equals("start")) {
				accountA.tell(new BankAccount.Deposit(taskCount * 100),
						getSelf());
				probe.tell("started", getSelf());

			} else if (msg.equals(BankAccount.TransactionStatus.DONE)) {
				log.info("deposit done");
				probe.tell("deposited", getSelf());
				for (int i = 0; i < tellerTxfrs; i++) {
					ActorRef txfr = getContext().actorOf(
							Props.create(BankTransfer.class), "aToBtxfr" + i);
					txfrs.add(txfr);
					txfr.tell(new BankTransfer.Transfer(accountA, accountB, 2),
							getSelf());
				}
			} else if (msg.equals(BankTransfer.TransferStatus.DONE)) {
				log.debug("txfr done");
				txfrs.remove(sender());
				txfrCount++;
				if (txfrCount % 100 == 0)
					log.info("Processed " + txfrCount + " txfrs");
				if (txfrCount == tellerTxfrs) {
					log.info("Processed all txfrs");
					probe.tell("done", getSelf());
				}
			} else if (msg.equals(BankTransfer.TransferStatus.FAILED)) {
				log.error("txfr failed");
			} else if (msg instanceof ActorRef) {
				probe = (ActorRef) msg;
			} else {
				log.error("unknown msg");
			}
		}

	}

	 @Test
	public void testSimultaneousDeposit() throws Exception {

		new JavaTestKit(system) {
			{

				final ActorRef accountA = system.actorOf(BankAccount
						.props(1, 0));

				// the run() method needs to finish within 3 seconds
				new Within(duration("30 seconds")) {
					protected void run() {

						final ActorRef testRef = getRef();

						final double depositAmt = 100;

						Callable<Boolean> task = new Callable<Boolean>() {
							@Override
							public Boolean call() {
								scala.concurrent.Future<Object> f = ask(
										accountA, new BankAccount.Deposit(
												depositAmt), t);
								try {
									Await.result(f, d);
									return true;
								} catch (Exception e) {
									e.printStackTrace();
									return false;
								}
							}
						};

						List<Callable<Boolean>> tasks = Collections.nCopies(
								taskCount, task);
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
							assertEquals(taskCount, futures.size());
							assertEquals(tasks.size() * depositAmt,
									balance, 1);
						} catch (Exception e) {
							e.printStackTrace();
							fail(e.getMessage());
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

				final Map<ActorRef, JavaTestKit> tellers = new HashMap<ActorRef, JavaTestKit>();

				for (int i = 0; i < threadCount; i++) {
					final ActorRef teller = system.actorOf(props);

					// can also use JavaTestKit “from the outside”
					final JavaTestKit probe = new JavaTestKit(system);
					// “inject” the probe by passing it to the test subject
					// like a real resource would be passed in production
					teller.tell(probe.getRef(), getRef());
					tellers.put(teller, probe);
				}

				// the run() method needs to finish within 3 seconds
				new Within(duration("1 minutes")) {
					protected void run() {

						for (Map.Entry<ActorRef, JavaTestKit> entry : tellers
								.entrySet()) {
							ActorRef teller = entry.getKey();
							JavaTestKit probe = entry.getValue();
							teller.tell("start", getRef());
							probe.expectMsgEquals(duration("1 second"),
									"started");

							probe.expectMsgEquals(duration("1 second"),
									"deposited");

							probe.expectMsgEquals(duration("10 seconds"), "done");
						}

						System.out.println("Executed all txfrs");
						// Will wait for the rest of the 3 seconds
						// expectNoMsg();
					}
				};
			}
		};
	}

}
